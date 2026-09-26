package dev.bryrich.credapp.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Canonical, explicitly workspace-scoped data shared by application adapters. No secrets
 * are returned here. SSNs are added separately through the existing audited access service. */
@Service
public class ApplicationDataService {
    public record Option(long id, String label) {}
    public record Source(String key, String label) {}
    public record Snapshot(String providerName, Map<String, String> values) {}
    private record Section(String prefix, String table, String columns, String order, int limit) {}
    private static final String PROVIDER = "first_name,last_name,dob,place_of_birth,npi,sex,phone_number,email_address,street_1,street_2,city,state,zip_code,us_citizen,ecfmg,degree,school_name,graduation_date,caqh_id,languages,prev_names";
    private static final String GROUP = "lbn,dba,npi,tax_id";
    private static final String LOCATION = "location_name,address,street1,street2,city,state,zip_code,phone_number,fax_number";
    private static final List<Section> SECTIONS = List.of(
            new Section("license", "licenses", "state,license_number,license_type,issue_date,expiration_date,status", "state,license_type,license_number,id", 10),
            new Section("training", "provider_training", "institution,training_type,specialty,city,state,start_date,end_date,completed,incomplete_reason", "start_date,id", 10),
            new Section("work", "provider_work_history", "entry_type,employer,position,city,state,start_date,end_date,reason_for_leaving,gap_explanation", "start_date,id", 10),
            new Section("reference", "provider_references", "name,title,relationship,email_address,phone_number,street_1,street_2,city,state,zip_code", "id", 5),
            new Section("certification", "certifications", "board,effective_date,expiration_date", "effective_date,id", 5),
            new Section("malpractice", "malpractice_policies", "policy_number,carrier_name,effective_date,expiration_date,type_of_coverage,amount_of_coverage_per_occurrence,amount_of_coverage_per_aggregate", "effective_date,id", 5),
            new Section("privilege", "hospital_privileges", "name,status,reappointment_date", "name,id", 5));
    private final JdbcTemplate jdbc;
    public ApplicationDataService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Source> sources() {
        List<Source> result = new ArrayList<>();
        result.add(new Source("provider.full_name", "Provider / Full name"));
        addSources(result, "provider", PROVIDER);
        result.add(new Source("provider.ssn", "Provider / SSN (audited access)"));
        addSources(result, "practice", GROUP);
        addSources(result, "location", LOCATION);
        for (Section section : SECTIONS) {
            for (int i = 1; i <= section.limit(); i++) addSources(result, section.prefix() + "." + i, section.columns());
        }
        return List.copyOf(result);
    }

    private static void addSources(List<Source> result, String prefix, String columns) {
        for (String column : columns.split(",")) result.add(new Source(prefix + "." + column,
                label(prefix) + " / " + label(column)));
    }

    private static String label(String key) {
        return switch (key) {
            case "dob" -> "Date of birth";
            case "npi" -> "NPI";
            case "lbn" -> "Legal business name";
            case "dba" -> "Doing business as";
            case "tax_id" -> "Tax ID";
            case "caqh_id" -> "CAQH ID";
            case "ecfmg" -> "ECFMG certificate number";
            case "street1", "street_1" -> "Street";
            case "street2", "street_2" -> "Street 2";
            case "zip_code" -> "ZIP code";
            case "us_citizen" -> "US citizen";
            case "prev_names" -> "Former names";
            default -> Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ').replace('.', ' ');
        };
    }

    @Transactional(readOnly = true)
    public Snapshot load(long workspace, long providerId, Long groupId, Long locationId) {
        Map<String, String> values = new LinkedHashMap<>();
        var providers = jdbc.queryForList("SELECT " + PROVIDER + " FROM providers WHERE user_group_id = ? AND id = ?", workspace, providerId);
        if (providers.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No such provider");
        copy(values, "provider", providers.getFirst());
        String name = values.get("provider.first_name") + " " + values.get("provider.last_name");
        values.put("provider.full_name", name);
        if (groupId != null) {
            if (groups(workspace, providerId).stream().noneMatch(g -> g.id() == groupId))
                throw new IllegalArgumentException("Choose one of this provider's practices");
            copy(values, "practice", jdbc.queryForMap("SELECT " + GROUP + " FROM groups WHERE user_group_id = ? AND id = ?", workspace, groupId));
        }
        if (locationId != null) {
            if (groupId == null || locations(workspace, providerId).stream().noneMatch(l -> l.id() == locationId))
                throw new IllegalArgumentException("Choose one of this provider's assigned locations and its practice");
            var locations = jdbc.queryForList("SELECT " + LOCATION + " FROM group_locations WHERE user_group_id = ? AND group_id = ? AND id = ?", workspace, groupId, locationId);
            if (locations.isEmpty()) throw new IllegalArgumentException("The location does not belong to the selected practice");
            copy(values, "location", locations.getFirst());
        }
        for (Section section : SECTIONS) {
            var rows = jdbc.queryForList("SELECT " + section.columns() + " FROM " + section.table()
                    + " WHERE user_group_id = ? AND provider_id = ? ORDER BY " + section.order() + " LIMIT " + section.limit(), workspace, providerId);
            for (int i = 0; i < rows.size(); i++) copy(values, section.prefix() + "." + (i + 1), rows.get(i));
        }
        return new Snapshot(name, Collections.unmodifiableMap(values));
    }

    @Transactional(readOnly = true)
    public List<Option> groups(long workspace, long providerId) {
        return jdbc.query("""
                SELECT g.id, g.lbn FROM groups g JOIN group_providers p ON p.group_id = g.id AND p.user_group_id = g.user_group_id
                WHERE g.user_group_id = ? AND p.provider_id = ? ORDER BY g.lbn, g.id
                """, (r, i) -> new Option(r.getLong(1), r.getString(2)), workspace, providerId);
    }
    @Transactional(readOnly = true)
    public List<Option> locations(long workspace, long providerId) {
        return jdbc.query("""
                SELECT l.id, g.lbn || ' / ' || l.location_name FROM group_locations l
                JOIN provider_locations p ON p.location_id = l.id AND p.user_group_id = l.user_group_id
                JOIN groups g ON g.id = l.group_id AND g.user_group_id = l.user_group_id
                WHERE l.user_group_id = ? AND p.provider_id = ? ORDER BY g.lbn, l.location_name, l.id
                """, (r, i) -> new Option(r.getLong(1), r.getString(2)), workspace, providerId);
    }
    private static void copy(Map<String, String> target, String prefix, Map<String, Object> row) {
        row.forEach((key, value) -> target.put(prefix + "." + key, value(value)));
    }
    private static String value(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean b) return b ? "Yes" : "No";
        if (value instanceof Array array) {
            try { return Arrays.stream((Object[]) array.getArray()).map(Object::toString).collect(Collectors.joining(", ")); }
            catch (SQLException e) { throw new IllegalStateException("Unable to read application data", e); }
        }
        return value.toString();
    }
}
