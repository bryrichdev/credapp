package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.application.ApplicationDataService;
import dev.bryrich.credapp.application.PdfApplicationService;
import dev.bryrich.credapp.document.DocumentCipher;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnAccessService;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Payer portal templates and the jobs that use them, from the app's pages. Every query names
 * the signed-in user's workspace. A job waits for one of its creator's connected browsers; see
 * {@link RunnerService} for the browser's side.
 */
@Service
public class PortalTemplateService {

    public record TemplateSummary(long id, long payerId, String payerName, String name, String startUrl,
                                  int revision, int fieldCount) {
        public boolean ready() {
            return revision > 0;
        }
    }

    public record Template(TemplateSummary summary, List<PortalField> fields) {
    }

    /** What the extension types into one box. The value is formatted already. */
    public record Answer(int field, String value) {
    }

    public record Job(long id, String kind, String templateName, String payerName, int revision, String status,
                      String result, String createdBy, Instant createdAt) {
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final DocumentCipher cipher;
    private final ApplicationDataService data;
    private final SsnAccessService ssns;

    public PortalTemplateService(JdbcTemplate jdbc, DocumentCipher cipher, ApplicationDataService data,
                                 SsnAccessService ssns) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.data = data;
        this.ssns = ssns;
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> templates(Long payerId) {
        actor(false);
        return jdbc.query("""
                        SELECT t.id, t.payer_id, p.name AS payer_name, t.name, t.start_url, t.revision,
                               coalesce(jsonb_array_length(v.fields), 0) AS field_count
                        FROM portal_templates t
                        JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                        LEFT JOIN portal_template_versions v ON v.template_id = t.id AND v.revision = t.revision
                        WHERE t.user_group_id = ?""" + (payerId == null ? "" : " AND t.payer_id = ?")
                        + " ORDER BY p.name, t.name, t.id",
                (r, i) -> new TemplateSummary(r.getLong("id"), r.getLong("payer_id"), r.getString("payer_name"),
                        r.getString("name"), r.getString("start_url"), r.getInt("revision"), r.getInt("field_count")),
                payerId == null ? new Object[]{workspace()} : new Object[]{workspace(), payerId});
    }

    /** Adds a template and asks the user's browser to open it in learn mode. */
    @Transactional
    public long create(long payerId, String name, String startUrl) {
        User user = actor(true);
        Integer payers = jdbc.queryForObject("SELECT count(*) FROM payers WHERE user_group_id = ? AND id = ?",
                Integer.class, workspace(), payerId);
        if (payers == null || payers == 0) {
            throw new ResponseStatusException(NOT_FOUND, "No such payer");
        }
        if (name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Enter a template name of up to 150 characters");
        }
        long id = jdbc.queryForObject("""
                        INSERT INTO portal_templates (user_group_id, payer_id, name, start_url, created_by)
                        VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, workspace(), payerId, name.trim(), checkUrl(startUrl), user.getEmail());
        learn(user, id, 0);
        return id;
    }

    /** Asks the user's browser to open the template in learn mode, starting from its current boxes. */
    @Transactional
    public void teach(long templateId) {
        User user = actor(true);
        learn(user, templateId, current(templateId).summary().revision());
    }

    /** A template to open in CredCloud's own browser, for someone who can edit. */
    @Transactional(readOnly = true)
    public TemplateSummary openable(long id) {
        actor(true);
        return current(id).summary();
    }

    @Transactional(readOnly = true)
    public Template template(long id) {
        actor(false);
        return current(id);
    }

    /** A template's boxes with one provider's answers, for copying by hand. */
    public record CopyView(TemplateSummary template, List<PortalField> fields, List<Answer> answers) {
    }

    /**
     * Captures one provider's answers for a portal and queues them for the user's browser. An
     * SSN is read, and logged, only when the template uses it.
     */
    @Transactional
    public long startFill(long providerId, long templateId, Long groupId, Long locationId, String ip) {
        User user = actor(true);
        Template template = ready(templateId);
        List<Answer> answers = answers(user, template, providerId, groupId, locationId, ip);
        return jdbc.queryForObject("""
                        INSERT INTO runner_jobs (user_group_id, user_id, kind, template_id, template_revision,
                                                 provider_id, answers, created_by)
                        VALUES (?, ?, 'fill', ?, ?, ?, ?, ?) RETURNING id""",
                Long.class, workspace(), user.getId(), templateId, template.summary().revision(), providerId,
                cipher.encrypt(JSON.writeValueAsBytes(answers)), user.getEmail());
    }

    /** The same answers a fill would type, shown to copy by hand. Nothing is kept. */
    @Transactional
    public CopyView copy(long providerId, long templateId, Long groupId, Long locationId, String ip) {
        User user = actor(true);
        Template template = ready(templateId);
        return new CopyView(template.summary(), template.fields(),
                answers(user, template, providerId, groupId, locationId, ip));
    }

    private Template ready(long templateId) {
        Template template = current(templateId);
        if (!template.summary().ready()) {
            throw new IllegalArgumentException("Teach CredCloud this portal before filling it");
        }
        return template;
    }

    private List<Answer> answers(User user, Template template, long providerId, Long groupId, Long locationId,
                                 String ip) {
        var snapshot = data.load(workspace(), providerId, groupId, locationId);
        Map<String, String> values = new LinkedHashMap<>(snapshot.values());
        if (template.fields().stream().anyMatch(f -> f.source().equals("provider.ssn"))) {
            String ssn = ssns.revealProviderSsn(user, providerId, ip);
            values.put("provider.ssn", ssn == null ? "" : ssn);
        }
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < template.fields().size(); i++) {
            PortalField field = template.fields().get(i);
            String value = field.source().isEmpty() ? "" : field.answerFormat().apply(values.getOrDefault(field.source(), ""));
            answers.add(new Answer(i, value.isEmpty() ? field.defaultValue() : value));
        }
        return answers;
    }

    @Transactional(readOnly = true)
    public List<Job> fills(long providerId) {
        actor(false);
        return jdbc.query("""
                        SELECT j.id, j.kind, t.name, p.name AS payer_name, j.template_revision, j.status,
                               j.result, j.created_by, j.created_at
                        FROM runner_jobs j
                        JOIN portal_templates t ON t.id = j.template_id AND t.user_group_id = j.user_group_id
                        JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                        WHERE j.user_group_id = ? AND j.provider_id = ? ORDER BY j.id DESC LIMIT 50""",
                (r, i) -> new Job(r.getLong("id"), r.getString("kind"), r.getString("name"), r.getString("payer_name"),
                        r.getInt("template_revision"), r.getString("status"), summary(r.getString("result")),
                        r.getString("created_by"), r.getTimestamp("created_at").toInstant()),
                workspace(), providerId);
    }

    /** Stops a job nobody has finished. Only the person who asked for it can. */
    @Transactional
    public void cancel(long jobId) {
        User user = actor(true);
        jdbc.update("""
                UPDATE runner_jobs SET status = 'cancelled', answers = NULL, finished_at = now()
                WHERE user_group_id = ? AND id = ? AND user_id = ? AND status IN ('waiting', 'claimed')""",
                workspace(), jobId, user.getId());
    }

    /** Saves a new version of a template's boxes. Called for a browser's learn job. */
    void saveVersion(long workspace, long templateId, String startUrl, List<PortalField> fields, String savedBy) {
        Set<String> sources = data.sources().stream().map(ApplicationDataService.Source::key).collect(Collectors.toSet());
        PortalField.check(fields, sources);
        Integer revision = jdbc.queryForObject(
                "SELECT revision FROM portal_templates WHERE user_group_id = ? AND id = ? FOR UPDATE",
                Integer.class, workspace, templateId);
        int next = revision + 1;
        jdbc.update("""
                        INSERT INTO portal_template_versions (user_group_id, template_id, revision, fields, created_by)
                        VALUES (?, ?, ?, ?::jsonb, ?)""",
                workspace, templateId, next, JSON.writeValueAsString(fields), savedBy);
        jdbc.update("UPDATE portal_templates SET revision = ?, start_url = coalesce(?, start_url) WHERE user_group_id = ? AND id = ?",
                next, startUrl == null || startUrl.isBlank() ? null : checkUrl(startUrl), workspace, templateId);
    }

    Template template(long workspace, long id, int revision) {
        return jdbc.query("""
                        SELECT t.id, t.payer_id, p.name AS payer_name, t.name, t.start_url, t.revision, v.fields
                        FROM portal_templates t
                        JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                        LEFT JOIN portal_template_versions v ON v.template_id = t.id AND v.revision = ?
                        WHERE t.user_group_id = ? AND t.id = ?""",
                (r, i) -> {
                    List<PortalField> fields = r.getString("fields") == null ? List.of()
                            : List.of(JSON.readValue(r.getString("fields"), PortalField[].class));
                    return new Template(new TemplateSummary(r.getLong("id"), r.getLong("payer_id"),
                            r.getString("payer_name"), r.getString("name"), r.getString("start_url"),
                            r.getInt("revision"), fields.size()), fields);
                },
                revision, workspace, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such portal template"));
    }

    List<Answer> answers(byte[] sealed) {
        return List.of(JSON.readValue(cipher.decrypt(sealed), Answer[].class));
    }

    private Template current(long id) {
        Integer revision = jdbc.query("SELECT revision FROM portal_templates WHERE user_group_id = ? AND id = ?",
                (r, i) -> r.getInt(1), workspace(), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such portal template"));
        return template(workspace(), id, revision);
    }

    private void learn(User user, long templateId, int revision) {
        jdbc.update("""
                        INSERT INTO runner_jobs (user_group_id, user_id, kind, template_id, template_revision, created_by)
                        VALUES (?, ?, 'learn', ?, ?, ?)""",
                workspace(), user.getId(), templateId, revision, user.getEmail());
    }

    /** Only https pages; a portal on plain http would send the provider's answers in the clear. */
    static String checkUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        try {
            URI uri = URI.create(trimmed);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && trimmed.length() <= 2000) {
                return trimmed;
            }
        } catch (IllegalArgumentException ignored) {
            // Falls through to the message below.
        }
        throw new IllegalArgumentException("Enter the portal's address, starting with https://");
    }

    private static String summary(String result) {
        if (result == null) {
            return "";
        }
        var node = JSON.readTree(result);
        int filled = node.path("filled").size();
        int missed = node.path("missed").size();
        return filled + " filled" + (missed > 0 ? ", " + missed + " not found" : "");
    }

    private static long workspace() {
        return PdfApplicationService.workspace();
    }

    private static User actor(boolean editing) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CredAppUserDetails principal)
                || !principal.isEnabled() || workspace() == 0) {
            throw new AccessDeniedException("Sign in to continue");
        }
        User user = principal.getUser();
        if (editing && (!user.getRole().canEdit() || ViewedGroup.currentId() != null)) {
            throw new AccessDeniedException("You cannot change portal templates in this workspace");
        }
        return user;
    }
}
