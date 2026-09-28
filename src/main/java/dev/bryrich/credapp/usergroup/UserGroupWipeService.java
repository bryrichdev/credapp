package dev.bryrich.credapp.usergroup;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deletes every record a user group has made (providers, groups, owners, payers and
 * everything hanging off them) and keeps the group itself and its accounts, so the same
 * people can sign in to an empty workspace.
 *
 * Works in plain SQL on an explicit table list rather than through the entities: some
 * foreign keys are RESTRICT on purpose (a provider with claims can't be deleted from the
 * UI), and a wipe has to get past them in a known order. A test checks this list against
 * every table with a user_group_id, so a new table can't be missed silently.
 */
@Service
public class UserGroupWipeService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupWipeService.class);

    /** What a wipe removes, in the order it's shown. */
    public static final Map<String, String> WIPED = labels(
            "providers", "Providers",
            "licenses", "Licenses",
            "certifications", "Board certifications",
            "provider_taxonomies", "Provider specialties",
            "group_providers", "Group memberships",
            "provider_locations", "Practice locations",
            "hospital_privileges", "Hospital privileges",
            "provider_references", "References",
            "criminal_charges", "Disclosures",
            "groups", "Groups",
            "group_locations", "Group locations",
            "group_taxonomies", "Group specialties",
            "owners", "Owners",
            "group_owners", "Ownership stakes",
            "group_owner_relationships", "Owner relationships",
            "malpractice_policies", "Malpractice policies",
            "malpractice_claims", "Malpractice claims",
            "payers", "Payers",
            "payer_contacts", "Payer contacts",
            "group_payers", "Group enrollments",
            "provider_payers", "Provider enrollments",
            "documents", "Documents",
            "provider_training", "Training",
            "provider_work_history", "Work history",
            "change_log", "Change history",
            "application_runs", "PDF applications",
            "application_templates", "Payer PDF templates",
            "runner_jobs", "Portal fills",
            "portal_template_versions", "Portal template versions",
            "portal_templates", "Payer portal templates");

    /** Children before parents, so no foreign key ever blocks a delete. */
    static final List<String> DELETE_ORDER = List.of(
            "change_log",
            "application_runs",
            "application_templates",
            "runner_jobs",
            "portal_template_versions",
            "portal_templates",
            "documents",
            "provider_training",
            "provider_work_history",
            "malpractice_claims",
            "criminal_charges",
            "hospital_privileges",
            "malpractice_policies",
            "provider_payers",
            "group_payers",
            "payer_contacts",
            "payers",
            "provider_locations",
            "group_owner_relationships",
            "group_owners",
            "group_providers",
            "group_locations",
            "group_taxonomies",
            "provider_taxonomies",
            "licenses",
            "certifications",
            "provider_references",
            "groups",
            "providers",
            "owners");

    /**
     * Group-scoped tables a wipe leaves alone: the accounts; the record of who looked at
     * which SSN or CAQH password, an audit trail of what people did rather than the practice's data;
     * the group's tracking settings, which are how the group works, not what it holds; and the
     * runners paired to its accounts.
     */
    static final Set<String> KEPT = Set.of("users", "password_reset_requests", "ssn_access_log",
            "caqh_password_access_log", "document_access_log", "application_access_log", "tracking_settings",
            "runners");

    /** Rows in one table, labelled for the page. */
    public record Count(String label, long rows) {
    }

    /** What a group has: the records a wipe would remove, and the accounts it keeps. */
    public record Contents(List<Count> records, long total, long accounts) {
        public boolean isEmpty() {
            return total == 0;
        }
    }

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public UserGroupWipeService(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Contents contents(Long userGroupId) {
        return count(userGroupId);
    }

    /**
     * Deletes it all in one transaction: either everything goes or nothing does.
     *
     * @return what was there before the wipe
     */
    @Transactional
    public Contents wipe(Long userGroupId, String wipedBy) {
        // Holding the group's row makes a second wipe of the same group wait for this one.
        jdbc.queryForObject("SELECT id FROM user_groups WHERE id = ? FOR UPDATE", Long.class, userGroupId);
        entityManager.flush();
        stopChangeHistory(jdbc);
        Contents before = count(userGroupId);
        for (String table : DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table + " WHERE user_group_id = ?", userGroupId);
        }
        // Anything this request already loaded from the group is gone now.
        entityManager.clear();
        log.warn("{} wiped user group {}: {} records deleted, {} accounts kept",
                wipedBy, userGroupId, before.total(), before.accounts());
        return before;
    }

    /**
     * Keeps this transaction's deletes out of the change history. Emptying a whole group isn't
     * a change to any one record, and the group's history goes with it.
     */
    static void stopChangeHistory(JdbcTemplate jdbc) {
        jdbc.queryForObject("SELECT set_config('credapp.audit_off', 'on', true)", String.class);
    }

    /** Counts in the caller's transaction, so a delete can count after taking its lock. */
    Contents count(Long userGroupId) {
        List<Count> records = new ArrayList<>();
        long total = 0;
        for (var table : WIPED.entrySet()) {
            Long rows = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table.getKey() + " WHERE user_group_id = ?", Long.class, userGroupId);
            if (rows != null && rows > 0) {
                records.add(new Count(table.getValue(), rows));
                total += rows;
            }
        }
        Long accounts = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE user_group_id = ?", Long.class, userGroupId);
        return new Contents(List.copyOf(records), total, accounts == null ? 0 : accounts);
    }

    private static Map<String, String> labels(String... pairs) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            labels.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(labels);
    }
}
