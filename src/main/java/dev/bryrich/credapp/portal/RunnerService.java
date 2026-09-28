package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.application.AnswerFormat;
import dev.bryrich.credapp.application.ApplicationDataService;
import dev.bryrich.credapp.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Connected browsers and their side of the jobs. The CredCloud extension connects with one
 * click on a page its owner is signed in to, and gets a device token for the runner API. Only
 * a hash of the token is stored. A browser only ever sees its own owner's jobs in its own
 * workspace. (The table is still called runners, after the first design.)
 */
@Service
public class RunnerService {

    /** Who a request's token belongs to. */
    public record Identity(long runnerId, long userId, long workspace, String email) {
    }

    public record Runner(long id, String name, Instant createdAt, Instant pairedAt, Instant lastSeenAt,
                         Instant revokedAt) {
        public String state() {
            return revokedAt != null ? "Revoked" : "Connected";
        }
    }

    /** A job as the runner gets it. answers is empty for learn jobs; sources and formats for fill jobs. */
    public record JobForRunner(long id, String kind, long templateId, String templateName, String payerName,
                               String startUrl, int revision, List<PortalField> fields, String providerName,
                               List<PortalTemplateService.Answer> answers,
                               List<ApplicationDataService.Source> sources, Map<String, String> formats) {
    }

    private final JdbcTemplate jdbc;
    private final PortalTemplateService templates;
    private final ApplicationDataService data;
    private final SecureRandom random = new SecureRandom();

    public RunnerService(JdbcTemplate jdbc, PortalTemplateService templates, ApplicationDataService data) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.data = data;
    }

    // --- From the app, as the signed-in user ---

    @Transactional(readOnly = true)
    public List<Runner> runners(User owner) {
        return jdbc.query("""
                        SELECT id, name, created_at, paired_at, last_seen_at, revoked_at FROM runners
                        WHERE user_group_id = ? AND user_id = ? ORDER BY id DESC""",
                (r, i) -> new Runner(r.getLong("id"), r.getString("name"), instant(r, "created_at"),
                        instant(r, "paired_at"), instant(r, "last_seen_at"), instant(r, "revoked_at")),
                owner.getUserGroupId(), owner.getId());
    }

    /**
     * Connects a browser for this user and returns its token, which the page hands straight
     * to the extension. It's shown nowhere else and can't be looked up again.
     */
    @Transactional
    public String connect(User owner, String name) {
        String cleaned = name == null || name.isBlank() ? "Chrome" : name.trim();
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        String token = token();
        jdbc.update("""
                        INSERT INTO runners (user_group_id, user_id, name, token_hash, paired_at)
                        VALUES (?, ?, ?, ?, now())""",
                owner.getUserGroupId(), owner.getId(), cleaned, hash(token));
        return token;
    }

    @Transactional
    public void revoke(User owner, long runnerId) {
        jdbc.update("""
                UPDATE runners SET revoked_at = coalesce(revoked_at, now()), token_hash = NULL
                WHERE user_group_id = ? AND user_id = ? AND id = ?""",
                owner.getUserGroupId(), owner.getId(), runnerId);
    }

    // --- From the browser ---

    /** The runner a token belongs to, if the token is live and its owner can still sign in. */
    @Transactional
    public Optional<Identity> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                        UPDATE runners r SET last_seen_at = now() FROM users u
                        WHERE r.token_hash = ? AND r.revoked_at IS NULL AND u.id = r.user_id
                          AND u.user_group_id = r.user_group_id AND u.is_enabled
                        RETURNING r.id, r.user_id, r.user_group_id, u.email""",
                (r, i) -> new Identity(r.getLong("id"), r.getLong("user_id"), r.getLong("user_group_id"),
                        r.getString("email")),
                hash(token)).stream().findFirst();
    }

    /**
     * The runner's next job: one it claimed and hasn't finished (it restarted), or the oldest
     * waiting one for its owner. Jobs left waiting a day are dropped.
     */
    @Transactional
    public Optional<JobForRunner> next(Identity runner) {
        jdbc.update("""
                UPDATE runner_jobs SET status = 'cancelled', answers = NULL, finished_at = now()
                WHERE user_group_id = ? AND user_id = ? AND status = 'waiting' AND created_at < now() - interval '1 day'""",
                runner.workspace(), runner.userId());
        record Row(long id, String kind, long templateId, int revision, Long providerId, byte[] answers) {
        }
        Optional<Row> row = jdbc.query("""
                        SELECT id, kind, template_id, template_revision, provider_id, answers FROM runner_jobs
                        WHERE user_group_id = ? AND user_id = ?
                          AND (status = 'waiting' OR (status = 'claimed' AND runner_id = ?))
                        ORDER BY (status = 'claimed') DESC, id LIMIT 1 FOR UPDATE SKIP LOCKED""",
                (r, i) -> new Row(r.getLong("id"), r.getString("kind"), r.getLong("template_id"),
                        r.getInt("template_revision"), r.getObject("provider_id", Long.class), r.getBytes("answers")),
                runner.workspace(), runner.userId(), runner.runnerId()).stream().findFirst();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Row job = row.get();
        jdbc.update("UPDATE runner_jobs SET status = 'claimed', runner_id = ?, claimed_at = coalesce(claimed_at, now()) WHERE id = ?",
                runner.runnerId(), job.id());
        var template = templates.template(runner.workspace(), job.templateId(), job.revision());
        var summary = template.summary();
        boolean fill = job.kind().equals("fill");
        String providerName = fill ? jdbc.queryForObject(
                "SELECT first_name || ' ' || last_name FROM providers WHERE user_group_id = ? AND id = ?",
                String.class, runner.workspace(), job.providerId()) : null;
        return Optional.of(new JobForRunner(job.id(), job.kind(), summary.id(), summary.name(), summary.payerName(),
                summary.startUrl(), job.revision(), template.fields(), providerName,
                fill ? templates.answers(job.answers()) : List.of(),
                fill ? List.of() : data.sources(),
                fill ? Map.of() : formats()));
    }

    /** Records what a fill job typed. Labels only; the answers themselves are cleared. */
    @Transactional
    public void finishFill(Identity runner, long jobId, List<String> filled, List<String> missed) {
        claimed(runner, jobId, "fill");
        var result = Map.of("filled", limit(filled), "missed", limit(missed));
        jdbc.update("""
                        UPDATE runner_jobs SET status = 'done', answers = NULL, result = ?::jsonb, finished_at = now()
                        WHERE id = ?""",
                PortalTemplateService.JSON.writeValueAsString(result), jobId);
    }

    /** Saves the boxes the coordinator showed the runner as the template's next version. */
    @Transactional
    public int finishLearn(Identity runner, long jobId, String startUrl, List<PortalField> fields) {
        long templateId = claimed(runner, jobId, "learn");
        templates.saveVersion(runner.workspace(), templateId, startUrl, fields, runner.email());
        jdbc.update("UPDATE runner_jobs SET status = 'done', finished_at = now() WHERE id = ?", jobId);
        return jdbc.queryForObject("SELECT revision FROM portal_templates WHERE id = ?", Integer.class, templateId);
    }

    @Transactional
    public void cancel(Identity runner, long jobId) {
        jdbc.update("""
                UPDATE runner_jobs SET status = 'cancelled', answers = NULL, finished_at = now()
                WHERE user_group_id = ? AND user_id = ? AND id = ? AND status IN ('waiting', 'claimed')""",
                runner.workspace(), runner.userId(), jobId);
    }

    /** @return the job's template id, once it's known to be this runner's to finish */
    private long claimed(Identity runner, long jobId, String kind) {
        record Row(String kind, String status, Long runnerId, long templateId) {
        }
        Row row = jdbc.query("""
                        SELECT kind, status, runner_id, template_id FROM runner_jobs
                        WHERE user_group_id = ? AND user_id = ? AND id = ? FOR UPDATE""",
                (r, i) -> new Row(r.getString("kind"), r.getString("status"), r.getObject("runner_id", Long.class),
                        r.getLong("template_id")),
                runner.workspace(), runner.userId(), jobId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such job"));
        if (!row.kind().equals(kind) || !row.status().equals("claimed") || row.runnerId() == null
                || row.runnerId() != runner.runnerId()) {
            throw new ResponseStatusException(CONFLICT, "This job isn't open on this runner");
        }
        return row.templateId();
    }

    private static List<String> limit(List<String> labels) {
        return labels == null ? List.of() : labels.stream().limit(PortalField.MAX_FIELDS)
                .map(label -> label == null ? "" : label.length() > 200 ? label.substring(0, 200) : label).toList();
    }

    private static Map<String, String> formats() {
        var formats = new java.util.LinkedHashMap<String, String>();
        for (AnswerFormat format : AnswerFormat.values()) {
            formats.put(format.name(), format.getLabel());
        }
        return formats;
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        var timestamp = r.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
