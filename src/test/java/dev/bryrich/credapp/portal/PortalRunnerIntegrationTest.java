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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pairing a runner, teaching it a portal, and filling a provider into it, end to end. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortalRunnerIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}");

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRunnerLearnsAPortalThenFillsAProviderIntoIt_andNeverKeepsTheAnswers() throws Exception {
        User admin = practice();
        long workspace = admin.getUserGroupId();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, workspace);
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        String token = pairedRunner(admin);

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
    void codesWorkOnce_tokensStopWhenRevoked_andRunnersOnlySeeTheirOwnersJobs() throws Exception {
        User admin = practice();
        String code = code(admin);
        String token = pair(code);
        mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/runner/api/jobs/next")).andExpect(status().isUnauthorized());
        mvc.perform(get("/runner/api/jobs/next").with(bearer("not-a-token"))).andExpect(status().isUnauthorized());
        // The runner's own tab loads without signing in; it holds no data.
        mvc.perform(get("/runner/home.html")).andExpect(status().isOk());
        // A token opens none of the app's pages: they still send you to sign in.
        mvc.perform(get("/providers").with(bearer(token))).andExpect(status().is3xxRedirection());

        User stranger = practice();
        String strangersToken = pairedRunner(stranger);
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, admin.getUserGroupId());
        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/runner/api/jobs/next").with(bearer(strangersToken))).andExpect(status().isNoContent());

        long runner = jdbc.queryForObject("SELECT id FROM runners WHERE user_id = ?", Long.class, admin.getId());
        mvc.perform(post("/account/runners/" + runner + "/revoke").with(signedIn(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/runner/api/jobs/next").with(bearer(token))).andExpect(status().isUnauthorized());
    }

    private JsonNode next(String token) throws Exception {
        String body = mvc.perform(get("/runner/api/jobs/next").with(bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private String pairedRunner(User owner) throws Exception {
        return pair(code(owner));
    }

    private String code(User owner) throws Exception {
        String page = mvc.perform(post("/account/runners").with(signedIn(owner)).with(csrf()).param("name", "Laptop"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher matcher = CODE.matcher(page);
        assertThat(matcher.find()).as("the page shows the code").isTrue();
        return matcher.group();
    }

    private String pair(String code) throws Exception {
        String body = mvc.perform(post("/runner/api/pair").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.toLowerCase() + "\"}"))
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
