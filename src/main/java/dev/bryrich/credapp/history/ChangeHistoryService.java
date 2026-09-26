package dev.bryrich.credapp.history;

import dev.bryrich.credapp.history.ChangeEntry.Action;
import dev.bryrich.credapp.history.ChangeEntry.FieldChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the change history the database triggers record (see V14) and puts it in words:
 * which record, which fields, and who.
 */
@Service
public class ChangeHistoryService {

    /** Entries per page; older ones are a link away. */
    public static final int PAGE = 100;

    public enum Subject { PROVIDER, GROUP }

    /** One page of history, newest first, and where the next (older) page starts. */
    public record Page(List<ChangeEntry> entries, Long olderThan) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Links to other records: shown by name in an item's label, never as a changed field. */
    private static final Set<String> LINKS = Set.of("id", "provider_id", "group_id", "payer_id", "location_id",
            "policy_id", "account_rep_id", "owner_id", "related_owner_id");

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("lbn", "Legal business name"), Map.entry("dba", "DBA"), Map.entry("npi", "NPI"),
            Map.entry("tax_id", "Tax ID"), Map.entry("caqh_id", "CAQH ID"), Map.entry("caqh_username", "CAQH username"),
            Map.entry("caqh_secret_ref", "CAQH password"), Map.entry("ssn", "SSN"), Map.entry("dob", "Date of birth"),
            Map.entry("is_primary", "Primary"), Map.entry("pcp_scp", "PCP or SCP"), Map.entry("us_citizen", "US citizen"),
            Map.entry("ecfmg", "ECFMG"), Map.entry("prev_names", "Previous names"), Map.entry("zip_code", "ZIP code"),
            Map.entry("street_1", "Street"), Map.entry("street_2", "Street, line 2"),
            Map.entry("tb_test_date", "TB test date"), Map.entry("content", "File"),
            Map.entry("doc_type", "Document type"), Map.entry("size_bytes", "Size"),
            Map.entry("amount_of_coverage_per_occurrence", "Coverage per occurrence"),
            Map.entry("amount_of_coverage_per_aggregate", "Coverage aggregate"));

    private final JdbcTemplate jdbc;

    public ChangeHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A provider's or group's history, newest first.
     *
     * @param olderThan the entry id to continue before, or null for the latest
     */
    @Transactional(readOnly = true)
    public Page history(long userGroupId, Subject subject, long subjectId, Long olderThan) {
        String which = subject == Subject.PROVIDER
                ? "c.provider_id = ?"
                // A group's page also covers its owners, whose records aren't tied to one group.
                : """
                  (c.group_id = ? OR (c.table_name = 'owners' AND c.row_id IN
                      (SELECT owner_id FROM group_owners WHERE group_id = ? AND user_group_id = c.user_group_id)))""";
        List<Object> args = new ArrayList<>(List.of(userGroupId, subjectId));
        if (subject == Subject.GROUP) {
            args.add(subjectId);
        }
        args.add(olderThan == null ? Long.MAX_VALUE : olderThan);
        List<Raw> rows = jdbc.query("""
                        SELECT c.id, c.changed_at, c.table_name, c.operation, c.row_data::text AS row_data,
                               c.changes::text AS changes, coalesce(u.full_name, c.actor_email) AS actor
                        FROM change_log c LEFT JOIN users u ON u.id = c.actor_id
                        WHERE c.user_group_id = ? AND %s AND c.id < ?
                        ORDER BY c.id DESC
                        LIMIT %d""".formatted(which, PAGE + 1),
                (row, i) -> new Raw(row.getLong("id"), row.getTimestamp("changed_at").toInstant(),
                        row.getString("table_name"), row.getString("operation"),
                        JSON.readTree(row.getString("row_data")),
                        row.getString("changes") == null ? null : JSON.readTree(row.getString("changes")),
                        row.getString("actor")),
                args.toArray());

        boolean more = rows.size() > PAGE;
        List<Raw> shown = more ? rows.subList(0, PAGE) : rows;
        Names names = names(userGroupId, shown);
        List<ChangeEntry> entries = shown.stream().map(raw -> entry(raw, subject, names)).toList();
        return new Page(entries, more ? shown.getLast().id() : null);
    }

    private record Raw(long id, java.time.Instant at, String table, String operation, JsonNode row,
                       JsonNode changes, String actor) {
    }

    private ChangeEntry entry(Raw raw, Subject subject, Names names) {
        JsonNode r = raw.row();
        String section;
        String item;
        switch (raw.table()) {
            case "providers" -> {
                section = "Provider details";
                item = null;
            }
            case "groups" -> {
                section = "Group details";
                item = null;
            }
            case "licenses" -> {
                section = "License";
                item = join(text(r, "state"), text(r, "license_number"));
            }
            case "certifications" -> {
                section = "Board certification";
                item = text(r, "board");
            }
            case "hospital_privileges" -> {
                section = "Hospital privileges";
                item = text(r, "name");
            }
            case "malpractice_policies" -> {
                section = "Malpractice policy";
                item = join(text(r, "carrier_name"), text(r, "policy_number"));
            }
            case "malpractice_claims" -> {
                section = "Malpractice claim";
                item = first(text(r, "claim_number"), text(r, "carrier_name"));
            }
            case "criminal_charges" -> {
                section = "Disclosure";
                item = first(text(r, "case_number"), humanize(text(r, "classification")));
            }
            case "provider_references" -> {
                section = "Reference";
                item = text(r, "name");
            }
            case "provider_taxonomies", "group_taxonomies" -> {
                section = "Specialty";
                item = names.taxonomy(text(r, "code"));
            }
            case "provider_training" -> {
                section = "Training";
                item = text(r, "institution");
            }
            case "provider_work_history" -> {
                section = "Work history";
                item = "GAP".equals(text(r, "entry_type")) ? "Time away" : text(r, "employer");
            }
            case "provider_payers", "group_payers" -> {
                section = "Payer enrollment";
                item = names.payer(id(r, "payer_id"));
            }
            case "payer_contacts" -> {
                section = "Payer contact";
                item = join(text(r, "name"), names.payer(id(r, "payer_id")));
            }
            case "provider_locations" -> {
                section = "Practice location";
                item = subject == Subject.PROVIDER ? names.location(id(r, "location_id"))
                        : join(names.provider(id(r, "provider_id")), "at", names.location(id(r, "location_id")));
            }
            case "group_providers" -> {
                section = subject == Subject.PROVIDER ? "Group" : "Provider";
                item = subject == Subject.PROVIDER ? names.group(id(r, "group_id")) : names.provider(id(r, "provider_id"));
            }
            case "documents" -> {
                section = "Document";
                item = first(text(r, "title"), text(r, "file_name"));
            }
            case "group_locations" -> {
                section = "Location";
                item = text(r, "location_name");
            }
            case "owners" -> {
                section = "Owner details";
                item = join(text(r, "first_name"), text(r, "last_name"));
            }
            case "group_owners" -> {
                section = "Owner";
                item = names.owner(id(r, "owner_id"));
            }
            case "group_owner_relationships" -> {
                section = "Owner relationship";
                item = join(names.owner(id(r, "owner_id")), "and", names.owner(id(r, "related_owner_id")));
            }
            default -> {
                section = humanize(raw.table());
                item = null;
            }
        }
        return new ChangeEntry(raw.id(), raw.at(), raw.actor(), Action.of(raw.operation()), section, item,
                fields(raw.changes()));
    }

    private static List<FieldChange> fields(JsonNode changes) {
        if (changes == null) {
            return List.of();
        }
        List<FieldChange> fields = new ArrayList<>();
        for (Map.Entry<String, JsonNode> change : changes.properties()) {
            String column = change.getKey();
            if (LINKS.contains(column)) {
                continue;
            }
            JsonNode value = change.getValue();
            if (value.has("masked")) {
                fields.add(new FieldChange(fieldLabel(column), null, null, true));
            } else {
                fields.add(new FieldChange(fieldLabel(column), value(value.get("from")), value(value.get("to")), false));
            }
        }
        return fields;
    }

    static String fieldLabel(String column) {
        String known = FIELD_LABELS.get(column);
        return known != null ? known : humanize(column);
    }

    /** A stored value as the page shows it: "Yes" for true, a blank for none. */
    static String value(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? "Yes" : "No";
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            node.forEach(part -> parts.add(value(part)));
            return String.join(", ", parts);
        }
        String text = node.asString();
        // Stored choices like IN_PROGRESS read better as "In progress"; short codes (UT, MD) stay.
        return text.matches("[A-Z][A-Z_]{3,}") ? humanize(text) : text;
    }

    /** "license_number" or "IN_PROGRESS" as "License number" or "In progress". */
    static String humanize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String words = name.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String text(JsonNode row, String column) {
        JsonNode node = row.get(column);
        return node == null || node.isNull() || node.asString().isBlank() ? null : node.asString();
    }

    private static Long id(JsonNode row, String column) {
        JsonNode node = row.get(column);
        return node == null || node.isNull() ? null : node.asLong();
    }

    private static String join(String... parts) {
        // A connecting word ("at", "and") only makes sense with something on each side.
        if (parts.length == 3 && (parts[0] == null || parts[2] == null)) {
            return first(parts[0], parts[2]);
        }
        String joined = Stream.of(parts).filter(part -> part != null).collect(Collectors.joining(" "));
        return joined.isEmpty() ? null : joined;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    // ---------- names for linked records ----------

    /** The names of the payers, locations and people these entries point at, fetched once per page. */
    private Names names(long userGroupId, List<Raw> rows) {
        Map<String, Set<Long>> wanted = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (Raw raw : rows) {
            for (String column : List.of("payer_id", "location_id", "group_id", "provider_id", "owner_id",
                    "related_owner_id")) {
                Long id = id(raw.row(), column);
                if (id != null) {
                    wanted.computeIfAbsent(column.equals("related_owner_id") ? "owner_id" : column,
                            key -> new HashSet<>()).add(id);
                }
            }
            String code = text(raw.row(), "code");
            if (code != null) {
                codes.add(code);
            }
        }
        return new Names(
                lookup("SELECT id, name FROM payers", userGroupId, wanted.get("payer_id")),
                lookup("SELECT id, location_name FROM group_locations", userGroupId, wanted.get("location_id")),
                lookup("SELECT id, lbn FROM groups", userGroupId, wanted.get("group_id")),
                lookup("SELECT id, concat_ws(' ', first_name, last_name) FROM providers", userGroupId,
                        wanted.get("provider_id")),
                lookup("SELECT id, concat_ws(' ', first_name, last_name) FROM owners", userGroupId,
                        wanted.get("owner_id")),
                taxonomies(codes));
    }

    private Map<Long, String> lookup(String select, long userGroupId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> found = new HashMap<>();
        jdbc.query(select + " WHERE user_group_id = ? AND id = ANY (?)",
                row -> {
                    found.put(row.getLong(1), row.getString(2));
                },
                userGroupId, ids.toArray(Long[]::new));
        return found;
    }

    private Map<String, String> taxonomies(Set<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> found = new HashMap<>();
        jdbc.query("SELECT code, specialty FROM taxonomies WHERE code = ANY (?)",
                row -> {
                    found.put(row.getString(1), row.getString(2));
                },
                (Object) codes.toArray(String[]::new));
        return found;
    }

    private record Names(Map<Long, String> payers, Map<Long, String> locations, Map<Long, String> groups,
                         Map<Long, String> providers, Map<Long, String> owners, Map<String, String> specialties) {

        // A record that's since been removed has no name left to show.
        String payer(Long id) {
            return id == null ? null : payers.getOrDefault(id, "a removed payer");
        }

        String location(Long id) {
            return id == null ? null : locations.getOrDefault(id, "a removed location");
        }

        String group(Long id) {
            return id == null ? null : groups.getOrDefault(id, "a removed group");
        }

        String provider(Long id) {
            return id == null ? null : providers.getOrDefault(id, "a removed provider");
        }

        String owner(Long id) {
            return id == null ? null : owners.getOrDefault(id, "a removed owner");
        }

        String taxonomy(String code) {
            if (code == null) {
                return null;
            }
            String specialty = specialties.get(code);
            return specialty == null ? code : specialty + " (" + code + ")";
        }
    }
}
