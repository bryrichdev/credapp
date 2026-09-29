package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CredCloud Helper from the server's side: connecting it with a one-time code, installing it,
 * teaching it a portal, filling a provider into it, keeping it up to date, and copying by hand.
 */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.base-url=https://credcloud.test",
        "credapp.helper.dir=${java.io.tmpdir}/credcloud-helper-builds"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortalRunnerIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("/helper/install/([A-Za-z0-9_-]{43})\\.sh");
    private static final byte[] MAC_BUILD = "a Mac build of the helper".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired RunnerService runners;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @BeforeAll
    static void builds() throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "credcloud-helper-builds");
        Files.createDirectories(dir);
        Files.write(dir.resolve("credcloud-helper-darwin-arm64"), MAC_BUILD);
    }

    @Test
    void aRunnerLearnsAPortalThenFillsAProviderIntoIt_andNeverKeepsTheAnswers() throws Exception {
        User admin = practice();
        long workspace = admin.getUserGroupId();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, workspace);
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        String token = connected(admin);

        mvc.perform(get("/runner/api/jobs/next").with(bearer(token))).andExpect(status().isNoContent());

        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "http://portal.example.com"))
                .andExpect(status().isOk());
        assertThat(count("portal_templates", workspace)).as("plain http is refused").isZero();
        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());

        JsonNode learn = next(token);
        assertThat(learn.path("kind").asString()).isEqualTo("learn");
        assertThat(learn.path("startUrl").asString()).isEqualTo("https://portal.example.com/enroll");
        assertThat(learn.path("sources").size()).isPositive();
        assertThat(learn.path("answers").size()).isZero();
        long learnJob = learn.path("id").asLong();

        List<Map<String, String>> fields = List.of(
                field("First name", "label", "First name", "text", "provider.first_name", "", ""),
                field("NPI", "css", "#npi", "text", "provider.npi", "DIGITS", ""),
                field("Accepting new patients", "label", "Accepting new patients", "checkbox", "", "", "Yes"));
        mvc.perform(post("/runner/api/jobs/" + learnJob + "/learned").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("fields", List.of(
                                field("Password", "label", "Password", "text", "provider.password", "", ""))))))
                .andExpect(status().isBadRequest());
        String saved = mvc.perform(post("/runner/api/jobs/" + learnJob + "/learned").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("fields", fields))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(saved).path("revision").asInt()).isEqualTo(1);
        long template = jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class, workspace);

        mvc.perform(post("/providers/" + provider + "/portal-fills").with(signedIn(admin)).with(csrf())
                        .param("templateId", Long.toString(template)))
                .andExpect(status().is3xxRedirection());
        JsonNode fill = next(token);
        assertThat(fill.path("kind").asString()).isEqualTo("fill");
        assertThat(fill.path("fields").size()).isEqualTo(3);
        String firstName = jdbc.queryForObject("SELECT first_name FROM providers WHERE id = ?", String.class, provider);
        assertThat(fill.path("answers").get(0).path("value").asString()).isEqualTo(firstName);
        assertThat(fill.path("answers").get(2).path("value").asString()).as("the fixed answer").isEqualTo("Yes");
        long fillJob = fill.path("id").asLong();

        mvc.perform(post("/runner/api/jobs/" + fillJob + "/filled").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filled\":[\"First name\",\"Accepting new patients\"],\"missed\":[\"NPI\"]}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM runner_jobs WHERE id = ?", String.class, fillJob)).isEqualTo("done");
        assertThat(jdbc.queryForObject("SELECT answers IS NULL FROM runner_jobs WHERE id = ?", Boolean.class, fillJob))
                .as("answers are cleared once the fill ends").isTrue();
        assertThat(jdbc.queryForObject("SELECT result->'missed'->>0 FROM runner_jobs WHERE id = ?", String.class, fillJob))
                .isEqualTo("NPI");
        mvc.perform(post("/runner/api/jobs/" + fillJob + "/filled").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"filled\":[],\"missed\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void tokensStopWhenDisconnected_andBrowsersOnlySeeTheirOwnersJobs() throws Exception {
        User admin = practice();
        String token = connected(admin);
        mvc.perform(get("/runner/api/jobs/next")).andExpect(status().isUnauthorized());
        mvc.perform(get("/runner/api/jobs/next").with(bearer("not-a-token"))).andExpect(status().isUnauthorized());
        // A token opens none of the app's pages: they still send you to sign in.
        mvc.perform(get("/providers").with(bearer(token))).andExpect(status().is3xxRedirection());
        // A code comes only from a signed-in page, with its CSRF token. Signed out, it's an
        // expired form: back to sign in. Signed in without the token, it looks like a forged
        // request: 403 (ExpiredFormHandler).
        long codesBefore = jdbc.queryForObject("SELECT count(*) FROM runner_pairing_codes", Long.class);
        mvc.perform(post("/helper/setup")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/helper/setup").with(signedIn(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/helper/connect").with(signedIn(admin))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runner_pairing_codes", Long.class)).isEqualTo(codesBefore);

        User stranger = practice();
        String strangersToken = connected(stranger);
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, admin.getUserGroupId());
        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/runner/api/jobs/next").with(bearer(strangersToken))).andExpect(status().isNoContent());

        long browser = jdbc.queryForObject("SELECT id FROM runners WHERE user_id = ?", Long.class, admin.getId());
        mvc.perform(post("/helper/computers/" + browser + "/revoke").with(signedIn(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/runner/api/jobs/next").with(bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void aHelperConnectsOnceWithACodeFromASignedInPage() throws Exception {
        User admin = practice();
        String code = installCode(admin);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runner_pairing_codes WHERE code_hash = ?",
                Long.class, (Object) RunnerService.hash(code))).as("only its hash is kept").isOne();

        String body = mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("code", code, "name", "Front desk iMac (Mac)"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode paired = JSON.readTree(body);
        assertThat(paired.path("email").asString()).isEqualTo(admin.getEmail());
        String token = paired.path("token").asString();
        mvc.perform(get("/runner/api/jobs/next").with(bearer(token))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT name FROM runners WHERE user_id = ?", String.class, admin.getId()))
                .isEqualTo("Front desk iMac (Mac)");

        // A code works once.
        mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("code", code, "name", "Another"))))
                .andExpect(status().isBadRequest());
        // And not after 30 minutes.
        String stale = runners.pairingCode(admin).code();
        jdbc.update("UPDATE runner_pairing_codes SET expires_at = now() - interval '1 minute' WHERE code_hash = ?",
                (Object) RunnerService.hash(stale));
        String refused = mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("code", stale, "name", "Late"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(refused).path("error").asString()).contains("expired");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runners WHERE user_id = ?", Long.class, admin.getId()))
                .isOne();
    }

    @Test
    void aHelperSkipsJobsItAlreadyHasOpen() throws Exception {
        User admin = practice();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class,
                admin.getUserGroupId());
        String token = connected(admin);
        for (String name : List.of("Enrollment", "Revalidation")) {
            mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                            .param("name", name).param("startUrl", "https://portal.example.com/" + name))
                    .andExpect(status().is3xxRedirection());
        }
        long first = next(token).path("id").asLong();
        assertThat(next(token).path("id").asLong()).as("a claimed job comes back after a restart").isEqualTo(first);
        String body = mvc.perform(get("/runner/api/jobs/next").param("skip", first + ",junk").with(bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long second = JSON.readTree(body).path("id").asLong();
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/runner/api/jobs/next").param("skip", first + "," + second).with(bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void theInstallCommandFetchesAScriptWithItsCode_andOnlyWhileTheCodeIsLive() throws Exception {
        User admin = practice();
        String page = mvc.perform(post("/helper/setup").with(signedIn(admin)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("curl -fsSL https://credcloud.test/helper/install/")
                .contains("irm https://credcloud.test/helper/install/");
        String code = installCode(admin);

        // No session: Terminal and PowerShell fetch these.
        String mac = mvc.perform(get("/helper/install/" + code + ".sh")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mac).startsWith("#!/bin/sh").contains("SERVER='https://credcloud.test'").contains("CODE='" + code + "'")
                .contains("/helper/download/credcloud-helper-darwin-$ARCH").contains("install --server");
        String windows = mvc.perform(get("/helper/install/" + code + ".ps1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(windows).contains("$code = '" + code + "'").contains("credcloud-helper-windows-amd64.exe");

        // Fetching the script doesn't use the code up; connecting does.
        mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("code", code, "name", "Front desk iMac (Mac)"))))
                .andExpect(status().isOk());
        assertThat(mvc.perform(get("/helper/install/" + code + ".sh")).andReturn().getResponse().getContentAsString())
                .contains("expired or was already used").doesNotContain(code);
        assertThat(mvc.perform(get("/helper/install/not-a-code.ps1")).andReturn().getResponse().getContentAsString())
                .contains("expired or was already used");
    }

    @Test
    void buildsAreServedToAnyone_andAHelperLearnsWhenItsBuildIsOutOfDate() throws Exception {
        byte[] build = mvc.perform(get("/helper/download/credcloud-helper-darwin-arm64"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"credcloud-helper-darwin-arm64\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(build).isEqualTo(MAC_BUILD);
        mvc.perform(get("/helper/download/credcloud-helper-windows-amd64.exe")).andExpect(status().isNotFound());
        mvc.perform(get("/helper/download/application.properties")).andExpect(status().isNotFound());

        User admin = practice();
        String token = connected(admin);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAC_BUILD));
        mvc.perform(get("/runner/api/jobs/next").with(bearer(token)).header("X-CredCloud-Helper", "darwin-arm64"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Helper-Sha256", sha256));
        assertThat(jdbc.queryForObject("SELECT platform FROM runners WHERE user_id = ?", String.class, admin.getId()))
                .isEqualTo("darwin-arm64");
        // No build for it here: no checksum, so it doesn't try to update.
        mvc.perform(get("/runner/api/jobs/next").with(bearer(token)).header("X-CredCloud-Helper", "windows-amd64"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("X-Helper-Sha256"));
    }

    @Test
    void thePageFollowsItsJob_andShowsWhyTheHelperCouldntOpenIt() throws Exception {
        User admin = practice();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class,
                admin.getUserGroupId());
        String none = mvc.perform(get("/helper/status").with(signedIn(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(none).path("connected").asBoolean()).isFalse();

        String token = connected(admin);
        var queued = mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Object job = queued.getFlashMap().get("helperJob");
        assertThat(job).as("the page gets the job to follow").isNotNull();

        String waiting = mvc.perform(get("/helper/status").param("job", job.toString()).with(signedIn(admin)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(waiting).path("job").path("status").asString()).isEqualTo("waiting");
        assertThat(JSON.readTree(waiting).path("running").asBoolean()).as("it hasn't asked for work yet").isFalse();

        assertThat(next(token).path("id").asLong()).isEqualTo(((Number) job).longValue());
        mvc.perform(post("/runner/api/jobs/" + job + "/cancel").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CredCloud Helper needs Google Chrome or Microsoft Edge.\"}"))
                .andExpect(status().isNoContent());
        String cancelled = mvc.perform(get("/helper/status").param("job", job.toString()).with(signedIn(admin)))
                .andReturn().getResponse().getContentAsString();
        JsonNode status = JSON.readTree(cancelled);
        assertThat(status.path("running").asBoolean()).isTrue();
        assertThat(status.path("job").path("status").asString()).isEqualTo("cancelled");
        assertThat(status.path("job").path("error").asString()).contains("Google Chrome");

        // Someone else's job isn't theirs to follow.
        User stranger = practice();
        String other = mvc.perform(get("/helper/status").param("job", job.toString()).with(signedIn(stranger)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(other).has("job")).isFalse();

        String helperPage = mvc.perform(get("/helper").with(signedIn(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(helperPage).contains("Test Mac (Mac)").contains("Running now");
        mvc.perform(get("/account/browsers").with(signedIn(admin))).andExpect(redirectedUrl("/helper"));
    }

    @Test
    void answersCanBeCopiedByHand_withoutQueueingAnything() throws Exception {
        User admin = practice();
        long workspace = admin.getUserGroupId();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, workspace);
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        String token = connected(admin);
        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());
        long learnJob = next(token).path("id").asLong();
        mvc.perform(post("/runner/api/jobs/" + learnJob + "/learned").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("fields", List.of(
                                field("First name", "label", "First name", "text", "provider.first_name", "UPPER", ""))))))
                .andExpect(status().isOk());
        long template = jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class, workspace);
        String firstName = jdbc.queryForObject("SELECT first_name FROM providers WHERE id = ?", String.class, provider);

        String page = mvc.perform(post("/providers/" + provider + "/portal-fills/copy").with(signedIn(admin)).with(csrf())
                        .param("templateId", Long.toString(template)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains(firstName.toUpperCase()).contains("data-copy");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runner_jobs WHERE kind = 'fill' AND user_group_id = ?",
                Long.class, workspace)).isZero();
    }

    private JsonNode next(String token) throws Exception {
        String body = mvc.perform(get("/runner/api/jobs/next").with(bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    /** The one-time code in the install command the helper page shows. */
    private String installCode(User owner) throws Exception {
        String page = mvc.perform(post("/helper/setup").with(signedIn(owner)).with(csrf())
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Chrome/141.0"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher matcher = CODE.matcher(page);
        assertThat(matcher.find()).as("the page shows the install command").isTrue();
        return matcher.group(1);
    }

    /** Connects a helper the way the install command does, and returns its token. */
    private String connected(User owner) throws Exception {
        String body = mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("code", installCode(owner), "name", "Test Mac (Mac)"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).path("token").asString();
    }

    private long count(String table, long workspace) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_group_id = ?", Long.class, workspace);
    }

    private static Map<String, String> field(String label, String by, String locator, String kind, String source,
                                             String format, String fallback) {
        return Map.of("label", label, "by", by, "locator", locator, "kind", kind, "source", source,
                "format", format, "defaultValue", fallback, "page", "/enroll");
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private User practice() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Portals " + UUID.randomUUID());
        User admin = registration.register(form);
        var principal = new CredAppUserDetails(admin);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        imports.importFile(TestWorkbook.fullPractice().bytes());
        SecurityContextHolder.clearContext();
        return admin;
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
