package dev.bryrich.credapp.application;

import dev.bryrich.credapp.document.*;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnAccessService;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.CurrentUserGroupResolver;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PdfApplicationService {
    /** format names an AnswerFormat; mappings saved before formats existed have none. */
    public record Mapping(PdfApplicationEngine.Field field, String source, boolean required, String defaultValue, String format) {
        public AnswerFormat answerFormat() { return AnswerFormat.of(format); }
    }
    public record Answer(PdfApplicationEngine.Field field, String source, boolean required, String value) {}
    public record TemplateSummary(long id, long payerId, String name, String payerName, boolean configured, int revision) {}
    public record Template(TemplateSummary summary, byte[] content, List<Mapping> mappings) {}
    public record Run(long id, long providerId, long templateId, String name, String payerName, int revision,
                      String status, Long documentId, String createdBy, Instant createdAt, List<Answer> answers) {
        private static final java.time.format.DateTimeFormatter WHEN =
                java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a z", Locale.US);

        /** In the server's zone; local-time.js shows it in the viewer's own. */
        public String createdLabel() { return WHEN.format(createdAt.atZone(java.time.ZoneId.systemDefault())); }
    }
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final DocumentCipher cipher;
    private final PdfApplicationEngine engine;
    private final ApplicationDataService data;
    private final DocumentService documents;
    private final SsnAccessService ssns;
    public PdfApplicationService(JdbcTemplate jdbc, DocumentCipher cipher, PdfApplicationEngine engine,
                                 ApplicationDataService data, DocumentService documents, SsnAccessService ssns) {
        this.jdbc = jdbc; this.cipher = cipher; this.engine = engine; this.data = data; this.documents = documents; this.ssns = ssns;
    }

    public static long workspace() { return new CurrentUserGroupResolver().resolveCurrentTenantIdentifier(); }
    private static User actor(boolean editing) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CredAppUserDetails principal)
                || !principal.isEnabled() || workspace() == 0) throw new AccessDeniedException("Sign in to continue");
        User user = principal.getUser();
        if (editing && (!user.getRole().canEdit() || ViewedGroup.currentId() != null))
            throw new AccessDeniedException("You cannot change applications in this workspace");
        return user;
    }

    @Transactional(readOnly = true)
    public String payerName(long payerId) {
        actor(false);
        return jdbc.query("SELECT name FROM payers WHERE user_group_id = ? AND id = ?",
                (r, i) -> r.getString(1), workspace(), payerId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such payer"));
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> templates(Long payerId) {
        actor(false);
        return jdbc.query("""
                SELECT t.id, t.payer_id, t.name, p.name AS payer_name, t.configured, t.revision
                FROM application_templates t JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                WHERE t.user_group_id = ?
                """ + (payerId == null ? "" : " AND t.payer_id = ?") + " ORDER BY p.name, t.name, t.id DESC",
                (r, i) -> new TemplateSummary(r.getLong("id"), r.getLong("payer_id"), r.getString("name"),
                        r.getString("payer_name"), r.getBoolean("configured"), r.getInt("revision")),
                payerId == null ? new Object[]{workspace()} : new Object[]{workspace(), payerId});
    }

    @Transactional
    public long upload(long payerId, String name, byte[] content) {
        User user = actor(true);
        payerName(payerId);
        if (name == null || name.isBlank() || name.length() > 150) throw new IllegalArgumentException("Enter a template name of up to 150 characters");
        // Stored without scripts: the template kept is the cleaned copy.
        byte[] clean = engine.sanitize(content);
        List<Mapping> mappings = engine.inspect(clean).stream().map(f -> new Mapping(f, "", f.required(), "", "")).toList();
        return jdbc.queryForObject("""
                INSERT INTO application_templates (user_group_id, payer_id, name, content, mappings, created_by)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, workspace(), payerId, name.trim(), cipher.encrypt(clean), seal(mappings), user.getEmail());
    }

    @Transactional(readOnly = true)
    public Template template(long id) { actor(false); return template(id, false); }
    private Template template(long id, boolean lock) {
        return jdbc.query("""
                SELECT t.*, p.name AS payer_name FROM application_templates t
                JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                WHERE t.user_group_id = ? AND t.id = ?
                """ + (lock ? " FOR UPDATE OF t" : ""),
                (r, i) -> new Template(new TemplateSummary(id, r.getLong("payer_id"), r.getString("name"),
                        r.getString("payer_name"), r.getBoolean("configured"), r.getInt("revision")),
                        cipher.decrypt(r.getBytes("content")), List.of(JSON.readValue(cipher.decrypt(r.getBytes("mappings")), Mapping[].class))),
                workspace(), id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such template"));
    }

    @Transactional
    public void saveMappings(long id, int revision, Map<String, String> params) {
        actor(true);
        Template template = template(id, true);
        if (template.summary().revision() != revision) throw new IllegalArgumentException("This template changed in another tab. Reload it before saving.");
        Set<String> sources = data.sources().stream().map(ApplicationDataService.Source::key).collect(Collectors.toSet());
        List<Mapping> mappings = new ArrayList<>();
        for (int i = 0; i < template.mappings().size(); i++) {
            Mapping old = template.mappings().get(i);
            String source = params.getOrDefault("source" + i, "");
            if (!source.isEmpty() && !sources.contains(source)) throw new IllegalArgumentException("Choose a listed data source");
            String fallback = params.getOrDefault("default" + i, "").trim();
            if (fallback.length() > old.field().maxLength()) throw new IllegalArgumentException("Default answer for " + old.field().label() + " is too long");
            String format = params.getOrDefault("format" + i, "");
            try {
                AnswerFormat.of(format);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Choose a listed format");
            }
            mappings.add(new Mapping(old.field(), source, old.field().required() || params.containsKey("required" + i),
                    fallback, format));
        }
        jdbc.update("UPDATE application_templates SET mappings = ?, configured = true, revision = revision + 1 WHERE user_group_id = ? AND id = ?",
                seal(mappings), workspace(), id);
    }

    @Transactional
    public long start(long providerId, long templateId, Long groupId, Long locationId, String ip) {
        User user = actor(true);
        Template template = template(templateId, true);
        if (!template.summary().configured()) throw new IllegalArgumentException("Finish mapping this payer's template first");
        var snapshot = data.load(workspace(), providerId, groupId, locationId);
        Map<String, String> values = new LinkedHashMap<>(snapshot.values());
        if (template.mappings().stream().anyMatch(m -> m.source().equals("provider.ssn"))) {
            String ssn = ssns.revealProviderSsn(user, providerId, ip);
            values.put("provider.ssn", ssn == null ? "" : ssn);
        }
        List<Answer> answers = template.mappings().stream().map(m -> new Answer(m.field(), m.source(), m.required(),
                m.source().isEmpty() ? m.defaultValue()
                        : m.answerFormat().apply(values.getOrDefault(m.source(), "")))).toList();
        return jdbc.queryForObject("""
                INSERT INTO application_runs (user_group_id, provider_id, template_id, template_revision,
                    practice_group_id, location_id, field_values, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, workspace(), providerId, templateId, template.summary().revision(), groupId, locationId, seal(answers), user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<Run> runs(long providerId) {
        actor(false);
        return queryRuns(" AND r.provider_id = ? ORDER BY r.id DESC LIMIT 100", providerId, false);
    }
    @Transactional
    public Run review(long id) {
        User user = actor(false);
        Run run = run(id, false);
        audit(run.id(), user, "review");
        return run;
    }
    private Run run(long id, boolean lock) {
        return queryRuns(" AND r.id = ?" + (lock ? " FOR UPDATE OF r" : ""), id, true).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such application"));
    }
    private List<Run> queryRuns(String where, long id, boolean answers) {
        return jdbc.query("""
                SELECT r.*, t.name, p.name AS payer_name FROM application_runs r
                JOIN application_templates t ON t.id = r.template_id AND t.user_group_id = r.user_group_id
                JOIN payers p ON p.id = t.payer_id AND p.user_group_id = t.user_group_id
                WHERE r.user_group_id = ?
                """ + where, (r, i) -> new Run(r.getLong("id"), r.getLong("provider_id"), r.getLong("template_id"),
                r.getString("name"), r.getString("payer_name"), r.getInt("template_revision"), r.getString("status"),
                r.getObject("document_id", Long.class), r.getString("created_by"), r.getTimestamp("created_at").toInstant(),
                answers ? List.of(JSON.readValue(cipher.decrypt(r.getBytes("field_values")), Answer[].class)) : List.of()), workspace(), id);
    }

    /** Row lock makes repeated/concurrent generate requests return the one saved result. */
    @Transactional
    public Run save(long id, Map<String, String> params, boolean generate) {
        User user = actor(true);
        Run run = run(id, true);
        if (run.status().equals("generated")) return run;
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < run.answers().size(); i++) {
            Answer old = run.answers().get(i);
            String value = params.getOrDefault("value" + i, "").trim();
            if (value.length() > old.field().maxLength()) throw new IllegalArgumentException(old.field().label() + " exceeds its maximum length");
            answers.add(new Answer(old.field(), old.source(), old.required(), value));
        }
        if (generate) {
            if (!"yes".equals(params.get("reviewed"))) throw new IllegalArgumentException("Confirm that you reviewed the answers before generating");
            List<String> missing = answers.stream().filter(a -> a.required() && a.value().isBlank()).map(a -> a.field().label()).toList();
            if (!missing.isEmpty()) throw new IllegalArgumentException("Complete required answers: " + String.join(", ", missing));
            Template template = template(run.templateId(), false);
            Map<String, String> values = answers.stream().collect(Collectors.toMap(a -> a.field().name(), Answer::value));
            byte[] pdf = engine.fill(template.content(), values);
            long doc = documents.upload(workspace(), DocumentService.Owner.PROVIDER, run.providerId(), DocumentType.OTHER,
                    run.payerName() + " / " + run.name(), "application-" + run.id() + ".pdf", pdf, null, user.getEmail());
            jdbc.update("""
                    UPDATE application_runs SET status = 'generated', field_values = ?, document_id = ?, generated_by = ?, generated_at = now()
                    WHERE user_group_id = ? AND id = ?
                    """, seal(answers), doc, user.getEmail(), workspace(), id);
            audit(id, user, "generate");
        } else {
            jdbc.update("UPDATE application_runs SET field_values = ? WHERE user_group_id = ? AND id = ?", seal(answers), workspace(), id);
            audit(id, user, "save_draft");
        }
        return run(id, false);
    }
    private byte[] seal(Object value) { return cipher.encrypt(JSON.writeValueAsBytes(value)); }
    private void audit(long id, User actor, String action) {
        jdbc.update("INSERT INTO application_access_log (user_group_id, run_id, user_id, user_email, action) VALUES (?, ?, ?, ?, ?)",
                workspace(), id, actor.getId(), actor.getEmail(), action);
    }
}
