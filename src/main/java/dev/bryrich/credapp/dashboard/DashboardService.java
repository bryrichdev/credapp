package dev.bryrich.credapp.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers and lists on the home page, for one user group. Plain SQL with the group
 * passed in, like the tracking report: it's a handful of counts across many tables.
 */
@Service
public class DashboardService {

    /** How many of each thing the group has on file. */
    public record Counts(long providers, long groups, long owners, long payers, long licenses) {
        public boolean isEmpty() {
            return providers == 0 && groups == 0 && owners == 0 && payers == 0;
        }
    }

    /** A provider or group, and when anything on its record last changed. */
    public record Recent(String kind, Long id, String name, Instant changedAt) {
        public String path() {
            return ("provider".equals(kind) ? "/providers/" : "/groups/") + id;
        }

        /** "just now", "5 minutes ago", "3 hours ago", "yesterday", "12 days ago". */
        public String ago() {
            long minutes = java.time.Duration.between(changedAt, Instant.now()).toMinutes();
            if (minutes < 1) {
                return "just now";
            }
            if (minutes < 60) {
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            }
            long days = hours / 24;
            return days == 1 ? "yesterday" : days + " days ago";
        }
    }

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Counts counts(Long group) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM providers WHERE user_group_id = ?) AS providers,
                       (SELECT count(*) FROM groups WHERE user_group_id = ?) AS groups,
                       (SELECT count(*) FROM owners WHERE user_group_id = ?) AS owners,
                       (SELECT count(*) FROM payers WHERE user_group_id = ?) AS payers,
                       (SELECT count(*) FROM licenses WHERE user_group_id = ?
                          AND status NOT IN ('inactive', 'surrendered', 'revoked')) AS licenses
                """, (rs, row) -> new Counts(rs.getLong("providers"), rs.getLong("groups"),
                rs.getLong("owners"), rs.getLong("payers"), rs.getLong("licenses")),
                group, group, group, group, group);
    }

    /**
     * Provider and group enrollments by status, in the order an application moves through,
     * with zeros for statuses nobody is in.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> enrollmentsByStatus(Long group) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("not_started", "in_progress", "submitted", "active", "denied", "terminated")) {
            counts.put(status, 0L);
        }
        jdbc.query("""
                SELECT status, count(*) AS n FROM (
                    SELECT status FROM provider_payers WHERE user_group_id = ?
                    UNION ALL
                    SELECT status FROM group_payers WHERE user_group_id = ?) e
                GROUP BY status
                """, rs -> {
            counts.put(rs.getString("status"), rs.getLong("n"));
        }, group, group);
        return counts;
    }

    /**
     * The providers and groups whose records changed most recently. A change anywhere on the
     * record counts (a license renewed, an enrollment moved on), not just the name and details.
     */
    @Transactional(readOnly = true)
    public List<Recent> recentlyUpdated(Long group, int limit) {
        return jdbc.query("""
                SELECT kind, id, name, changed_at FROM (
                    SELECT 'provider' AS kind, p.id, p.last_name || ', ' || p.first_name AS name,
                           GREATEST(p.updated_at,
                               (SELECT max(updated_at) FROM licenses WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM certifications WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM hospital_privileges WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM provider_payers WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM provider_taxonomies WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM provider_references WHERE provider_id = p.id),
                               (SELECT max(updated_at) FROM malpractice_policies WHERE provider_id = p.id)) AS changed_at
                    FROM providers p WHERE p.user_group_id = ?
                    UNION ALL
                    SELECT 'group', g.id, g.lbn,
                           GREATEST(g.updated_at,
                               (SELECT max(updated_at) FROM group_locations WHERE group_id = g.id),
                               (SELECT max(updated_at) FROM group_owners WHERE group_id = g.id),
                               (SELECT max(updated_at) FROM group_payers WHERE group_id = g.id),
                               (SELECT max(updated_at) FROM group_taxonomies WHERE group_id = g.id),
                               (SELECT max(updated_at) FROM malpractice_policies WHERE group_id = g.id))
                    FROM groups g WHERE g.user_group_id = ?) r
                ORDER BY changed_at DESC, name
                LIMIT ?
                """, (rs, row) -> new Recent(rs.getString("kind"), rs.getLong("id"), rs.getString("name"),
                rs.getObject("changed_at", OffsetDateTime.class).toInstant()), group, group, limit);
    }

    /** Accounts that asked to join and are waiting for an admin. */
    @Transactional(readOnly = true)
    public long pendingApprovals(Long group) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE user_group_id = ? AND membership_status = 'PENDING'",
                Long.class, group);
        return count == null ? 0 : count;
    }
}
