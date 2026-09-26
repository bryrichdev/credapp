package dev.bryrich.credapp.tracking;

import dev.bryrich.credapp.tracking.TrackedItem.Subject;
import dev.bryrich.credapp.tracking.TrackedItem.SubjectType;
import dev.bryrich.credapp.usergroup.CurrentUserGroupResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything in one user group that has expired, is coming due or has been waiting too
 * long, worked out fresh from the records each time it's asked.
 *
 * Plain SQL with the group passed in, rather than through the entities: it reads a dozen
 * tables for a few columns each, and "has this been renewed?" is easiest said as NOT EXISTS.
 *
 * Rules worth knowing:
 * - A license, board certification or malpractice policy that's been renewed doesn't nag.
 *   Renewed means the same provider or group has a later one of the same kind: a license
 *   for the same state and type, a certification from the same board, or any malpractice
 *   policy (switching carriers is common).
 * - Licenses marked inactive, surrendered or revoked aren't tracked: nobody's keeping them up.
 * - Flu shots and TB tests are due a year after the date on file; CAQH attestation is due
 *   the group's CAQH interval after the last attestation.
 * - Recredentialing isn't tracked for denied or terminated enrollments.
 * - A follow-up date shows once it's within the due-soon window: it's a reminder, and
 *   listing it months ahead would be noise.
 * - An application is stalled once it's been submitted or in progress for the group's
 *   stalled period with no follow-up date set. Waiting is counted from the submitted date,
 *   or from the last change when there isn't one.
 */
@Service
public class TrackingService {

    /** Statuses of a license nobody is keeping up any more. */
    private static final String RETIRED_LICENSE = "('inactive', 'surrendered', 'revoked')";

    private final JdbcTemplate jdbc;
    private final TrackingSettingsService settingsService;

    public TrackingService(JdbcTemplate jdbc, TrackingSettingsService settingsService) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
    }

    /** The whole list, most pressing first: overdue, then due soon, stalled, then coming up. */
    @Transactional(readOnly = true)
    public Report report(Long userGroupId, LocalDate today) {
        TrackingSettings settings = settingsService.forGroup(userGroupId);
        Window window = new Window(today, settings);
        List<TrackedItem> items = new ArrayList<>();
        licenses(userGroupId, window, items);
        certifications(userGroupId, window, items);
        policies(userGroupId, window, items);
        documents(userGroupId, window, items);
        reappointments(userGroupId, window, items);
        providerDates(userGroupId, window, settings, items);
        enrollments(userGroupId, window, settings, items);
        items.sort(Comparator.comparing(TrackedItem::state)
                .thenComparing(item -> item.state() == TrackedState.STALLED ? -item.days() : item.days())
                .thenComparing(item -> item.subject().name(), String.CASE_INSENSITIVE_ORDER));
        return new Report(List.copyOf(items), settings);
    }

    /** The report for the group being worked in (see CurrentUserGroupResolver), as of today. */
    @Transactional(readOnly = true)
    public Report currentReport() {
        return report(currentGroup(), LocalDate.now());
    }

    /** How many things need doing now in the current group: the Tracking link's count. */
    @Transactional(readOnly = true)
    public long needingAction() {
        return currentReport().needingAction();
    }

    /** What needs attention for one provider, for the top of their page. */
    @Transactional(readOnly = true)
    public List<TrackedItem> forProvider(Long providerId) {
        return currentReport().forProvider(providerId);
    }

    /** What needs attention for one practice group's own records. */
    @Transactional(readOnly = true)
    public List<TrackedItem> forGroup(Long groupId) {
        return currentReport().forGroup(groupId);
    }

    /** Every enrollment with one payer that needs attention, across providers and groups. */
    @Transactional(readOnly = true)
    public List<TrackedItem> forPayer(Long payerId) {
        return currentReport().items().stream().filter(item -> payerId.equals(item.payerId())).toList();
    }

    public static Long currentGroup() {
        return new CurrentUserGroupResolver().resolveCurrentTenantIdentifier();
    }

    /** The providers in one practice group, for narrowing the list to that practice. */
    @Transactional(readOnly = true)
    public Set<Long> providersInGroup(Long userGroupId, Long groupId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT provider_id FROM group_providers WHERE user_group_id = ? AND group_id = ?",
                Long.class, userGroupId, groupId));
    }

    /** What a report found, and the settings it was worked out with. */
    public record Report(List<TrackedItem> items, TrackingSettings settings) {

        public Map<TrackedState, Long> countsByState() {
            Map<TrackedState, Long> counts = new EnumMap<>(TrackedState.class);
            for (TrackedState state : TrackedState.values()) {
                counts.put(state, 0L);
            }
            items.forEach(item -> counts.merge(item.state(), 1L, Long::sum));
            return counts;
        }

        /** Overdue, due soon and stalled: what the nav counts. */
        public long needingAction() {
            return items.stream().filter(item -> item.state().needsAction()).count();
        }

        public List<TrackedItem> forProvider(Long providerId) {
            return items.stream().filter(item -> item.subject().type() == SubjectType.PROVIDER
                    && item.subject().id().equals(providerId)).toList();
        }

        public List<TrackedItem> forGroup(Long groupId) {
            return items.stream().filter(item -> item.subject().type() == SubjectType.GROUP
                    && item.subject().id().equals(groupId)).toList();
        }
    }

    // ============ each source ============

    private void licenses(Long group, Window window, List<TrackedItem> items) {
        jdbc.query("""
                SELECT l.expiration_date AS due, l.state, l.license_type, l.license_number,
                       p.id AS provider_id, p.first_name, p.last_name
                FROM licenses l JOIN providers p ON p.id = l.provider_id
                WHERE l.user_group_id = ? AND l.expiration_date <= ? AND l.status NOT IN %s
                  AND NOT EXISTS (
                      SELECT 1 FROM licenses r
                      WHERE r.user_group_id = l.user_group_id AND r.provider_id = l.provider_id
                        AND r.id <> l.id AND upper(r.state) = upper(l.state)
                        AND upper(r.license_type) = upper(l.license_type)
                        AND r.expiration_date > l.expiration_date AND r.status NOT IN %s)
                """.formatted(RETIRED_LICENSE, RETIRED_LICENSE),
                rs -> {
                    add(items, window, TrackedKind.LICENSE, date(rs, "due"),
                            rs.getString("state") + " " + rs.getString("license_type") + " license "
                                    + rs.getString("license_number"),
                            provider(rs), null);
                }, group, window.horizon());
    }

    private void certifications(Long group, Window window, List<TrackedItem> items) {
        jdbc.query("""
                SELECT c.expiration_date AS due, c.board, p.id AS provider_id, p.first_name, p.last_name
                FROM certifications c JOIN providers p ON p.id = c.provider_id
                WHERE c.user_group_id = ? AND c.expiration_date <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM certifications r
                      WHERE r.user_group_id = c.user_group_id AND r.provider_id = c.provider_id
                        AND r.id <> c.id AND lower(r.board) = lower(c.board)
                        AND (r.expiration_date IS NULL OR r.expiration_date > c.expiration_date))
                """,
                rs -> {
                    add(items, window, TrackedKind.CERTIFICATION, date(rs, "due"), rs.getString("board"),
                            provider(rs), null);
                }, group, window.horizon());
    }

    private void policies(Long group, Window window, List<TrackedItem> items) {
        jdbc.query("""
                SELECT m.expiration_date AS due, m.carrier_name, m.policy_number,
                       m.provider_id, p.first_name, p.last_name, m.group_id, g.lbn
                FROM malpractice_policies m
                LEFT JOIN providers p ON p.id = m.provider_id
                LEFT JOIN groups g ON g.id = m.group_id
                WHERE m.user_group_id = ? AND m.expiration_date <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM malpractice_policies r
                      WHERE r.user_group_id = m.user_group_id AND r.id <> m.id
                        AND (r.provider_id = m.provider_id OR r.group_id = m.group_id)
                        AND (r.expiration_date IS NULL OR r.expiration_date > m.expiration_date))
                """,
                rs -> {
                    Subject subject = rs.getObject("provider_id") != null ? provider(rs)
                            : new Subject(SubjectType.GROUP, rs.getLong("group_id"), rs.getString("lbn"));
                    add(items, window, TrackedKind.MALPRACTICE_POLICY, date(rs, "due"),
                            rs.getString("carrier_name") + " " + rs.getString("policy_number"), subject, null);
                }, group, window.horizon());
    }

    private void reappointments(Long group, Window window, List<TrackedItem> items) {
        jdbc.query("""
                SELECT h.reappointment_date AS due, h.name, p.id AS provider_id, p.first_name, p.last_name
                FROM hospital_privileges h JOIN providers p ON p.id = h.provider_id
                WHERE h.user_group_id = ? AND h.reappointment_date <= ?
                """,
                rs -> {
                    add(items, window, TrackedKind.REAPPOINTMENT, date(rs, "due"), rs.getString("name"),
                            provider(rs), null);
                }, group, window.horizon());
    }

    /** Flu shot and TB test a year on, and CAQH attestation the group's interval on. */
    private void providerDates(Long group, Window window, TrackingSettings settings, List<TrackedItem> items) {
        jdbc.query("""
                SELECT id AS provider_id, first_name, last_name, flu_shot_date, tb_test_date, caqh_attested_date
                FROM providers
                WHERE user_group_id = ?
                  AND (flu_shot_date <= ?::date - INTERVAL '1 year'
                       OR tb_test_date <= ?::date - INTERVAL '1 year'
                       OR caqh_attested_date <= ?::date - ?)
                """,
                rs -> {
                    Subject subject = provider(rs);
                    LocalDate flu = date(rs, "flu_shot_date");
                    if (flu != null) {
                        add(items, window, TrackedKind.FLU_SHOT, flu.plusYears(1), "Last shot " + flu, subject, null);
                    }
                    LocalDate tb = date(rs, "tb_test_date");
                    if (tb != null) {
                        add(items, window, TrackedKind.TB_TEST, tb.plusYears(1), "Last test " + tb, subject, null);
                    }
                    LocalDate caqh = date(rs, "caqh_attested_date");
                    if (caqh != null) {
                        add(items, window, TrackedKind.CAQH_ATTESTATION, caqh.plusDays(settings.getCaqhDays()),
                                "Last attested " + caqh, subject, null);
                    }
                }, group, window.horizon(), window.horizon(), window.horizon(), settings.getCaqhDays());
    }

    /**
     * Documents with an expiration date. A newer document of the same kind for the same
     * provider or group that expires later counts as its renewal.
     */
    private void documents(Long group, Window window, List<TrackedItem> items) {
        jdbc.query("""
                SELECT d.expiration_date AS due, d.doc_type, d.title,
                       p.id AS provider_id, p.first_name, p.last_name, g.id AS group_id, g.lbn
                FROM documents d
                LEFT JOIN providers p ON p.id = d.provider_id
                LEFT JOIN groups g ON g.id = d.group_id
                WHERE d.user_group_id = ? AND d.expiration_date IS NOT NULL AND d.expiration_date <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM documents r
                      WHERE r.user_group_id = d.user_group_id AND r.doc_type = d.doc_type AND r.id <> d.id
                        AND r.provider_id IS NOT DISTINCT FROM d.provider_id
                        AND r.group_id IS NOT DISTINCT FROM d.group_id
                        AND (r.expiration_date IS NULL OR r.expiration_date > d.expiration_date))
                """, rs -> {
            rs.getLong("provider_id");
            Subject subject = rs.wasNull()
                    ? new Subject(SubjectType.GROUP, rs.getLong("group_id"), rs.getString("lbn"))
                    : provider(rs);
            String title = rs.getString("title");
            String what = dev.bryrich.credapp.document.DocumentType.fromValue(rs.getString("doc_type")).getLabel()
                    + (title == null ? "" : " (" + title + ")");
            add(items, window, TrackedKind.DOCUMENT, date(rs, "due"), what, subject, null);
        }, group, window.horizon());
    }

    private void enrollments(Long group, Window window, TrackingSettings settings, List<TrackedItem> items) {
        String sql = """
                SELECT e.status, e.recredential_date, e.follow_up_date,
                       COALESCE(e.submitted_date, e.updated_at::date) AS waiting_since,
                       y.id AS payer_id, y.name AS payer_name, %s
                FROM %s e JOIN payers y ON y.id = e.payer_id %s
                WHERE e.user_group_id = ?
                """;
        LocalDate followUpHorizon = window.today().plusDays(settings.getUrgentDays());
        LocalDate stalledBefore = window.today().minusDays(settings.getStalledDays());
        List.of(
                sql.formatted("p.id AS provider_id, p.first_name, p.last_name", "provider_payers",
                        "JOIN providers p ON p.id = e.provider_id"),
                sql.formatted("g.id AS group_id, g.lbn", "group_payers", "JOIN groups g ON g.id = e.group_id")
        ).forEach(query -> jdbc.query(query, rs -> {
            Subject subject = hasColumn(rs, "provider_id") ? provider(rs)
                    : new Subject(SubjectType.GROUP, rs.getLong("group_id"), rs.getString("lbn"));
            String payer = rs.getString("payer_name");
            Long payerId = rs.getLong("payer_id");
            String status = rs.getString("status");

            LocalDate recredential = date(rs, "recredential_date");
            if (recredential != null && !recredential.isAfter(window.horizon())
                    && !status.equals("denied") && !status.equals("terminated")) {
                add(items, window, TrackedKind.RECREDENTIAL, recredential, payer, subject, payerId);
            }
            LocalDate followUp = date(rs, "follow_up_date");
            if (followUp != null && !followUp.isAfter(followUpHorizon)) {
                add(items, window, TrackedKind.FOLLOW_UP, followUp, payer, subject, payerId);
            }
            LocalDate since = date(rs, "waiting_since");
            if (followUp == null && (status.equals("submitted") || status.equals("in_progress"))
                    && since != null && !since.isAfter(stalledBefore)) {
                items.add(new TrackedItem(TrackedKind.STALLED, TrackedState.STALLED, since,
                        ChronoUnit.DAYS.between(since, window.today()),
                        payer + (status.equals("submitted") ? ", submitted" : ", in progress"), subject, payerId));
            }
        }, group));
    }

    // ============ helpers ============

    /** Today, how far ahead to look, and where "due soon" starts. */
    private record Window(LocalDate today, int urgentDays, int warningDays) {
        Window(LocalDate today, TrackingSettings settings) {
            this(today, settings.getUrgentDays(), settings.getWarningDays());
        }

        LocalDate horizon() {
            return today.plusDays(warningDays);
        }

        TrackedState stateFor(long days) {
            if (days < 0) {
                return TrackedState.OVERDUE;
            }
            return days <= urgentDays ? TrackedState.DUE_SOON : TrackedState.COMING_UP;
        }
    }

    private static void add(List<TrackedItem> items, Window window, TrackedKind kind, LocalDate due,
                            String what, Subject subject, Long payerId) {
        long days = ChronoUnit.DAYS.between(window.today(), due);
        if (days > window.warningDays()) {
            return;
        }
        items.add(new TrackedItem(kind, window.stateFor(days), due, days, what, subject, payerId));
    }

    private static Subject provider(ResultSet rs) throws SQLException {
        return new Subject(SubjectType.PROVIDER, rs.getLong("provider_id"),
                rs.getString("last_name") + ", " + rs.getString("first_name"));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnLabel(i).equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }
}
