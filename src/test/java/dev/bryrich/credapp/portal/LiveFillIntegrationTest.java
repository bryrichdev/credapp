package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.portal.remote.RemoteBrowsers;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filling a provider into a portal from CredCloud's own browser. The portal address can't be
 * reached from the test, which is fine: what's tested is the job, the page and who can use it.
 */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.remote-browser.max-sessions=2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LiveFillIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired OnboardingImportService imports;
    @Autowired PortalTemplateService portals;
    @Autowired RemoteBrowsers browsers;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void closeAll() throws Exception {
        browsers.closeAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void aFillKeepsItsAnswersInTheBrowserAndRecordsOnlyWhatItFilled() throws Exception {
        User admin = practice();
        User stranger = practice();
        long workspace = admin.getUserGroupId();
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        String firstName = jdbc.queryForObject("SELECT first_name FROM providers WHERE id = ?", String.class, provider);
        long template = taughtTemplate(admin);

        String page = mvc.perform(post("/providers/" + provider + "/portal-fills/live").with(signedIn(admin)).with(csrf())
                        .param("templateId", Long.toString(template)))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(page).matches("/live/[A-Za-z0-9_-]{32}");
        long job = jdbc.queryForObject("SELECT max(id) FROM runner_jobs WHERE user_group_id = ? AND kind = 'fill'",
                Long.class, workspace);
        assertThat(jdbc.queryForObject("SELECT status FROM runner_jobs WHERE id = ?", String.class, job)).isEqualTo("claimed");
        assertThat(jdbc.queryForObject("SELECT answers IS NULL AND runner_id IS NULL FROM runner_jobs WHERE id = ?",
                Boolean.class, job)).isTrue();

        mvc.perform(get(page).with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Filling " + firstName)))
                .andExpect(content().string(containsString("Fill this page")));
        mvc.perform(get("/providers/" + provider + "/portal-fills").with(signedIn(admin)))
                .andExpect(content().string(containsString("Open now")));

        // The browser is on Chromium's error page, not the portal, so nothing is typed.
        mvc.perform(post(page + "/fill").with(signedIn(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("isn't on the portal")))
                .andExpect(jsonPath("$.done").value(0))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(post(page + "/fill").with(signedIn(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(page + "/fill").with(signedIn(admin))).andExpect(status().isForbidden());

        mvc.perform(post(page + "/close").with(signedIn(admin)).with(csrf()))
                .andExpect(redirectedUrl("/providers/" + provider + "/portal-fills"))
                .andExpect(flash().attribute("message", containsString("filled 0 of 2 boxes")));
        assertThat(jdbc.queryForObject("SELECT status FROM runner_jobs WHERE id = ?", String.class, job))
                .as("a fill that filled nothing is cancelled").isEqualTo("cancelled");
    }

    @Test
    void aFillThatFilledSomethingIsRecordedOnce() {
        User admin = practice();
        long workspace = admin.getUserGroupId();
        long job = claimedJob(admin);

        portals.endLiveFill(workspace, job, List.of("First name"), List.of("Accepting new patients"));
        portals.endLiveFill(workspace, job, List.of(), List.of("First name", "Accepting new patients"));

        assertThat(jdbc.queryForObject("SELECT status FROM runner_jobs WHERE id = ?", String.class, job)).isEqualTo("done");
        assertThat(jdbc.queryForObject("SELECT result->'filled'->>0 FROM runner_jobs WHERE id = ?", String.class, job))
                .isEqualTo("First name");
    }

    @Test
    void fillsLeftOpenWhenTheAppStoppedAreClosedAtStart() {
        User admin = practice();
        long job = claimedJob(admin);
        portals.closeLeftoverLiveFills();
        assertThat(jdbc.queryForObject("SELECT status FROM runner_jobs WHERE id = ?", String.class, job))
                .isEqualTo("cancelled");
    }

    // ============ helpers ============

    private long taughtTemplate(User admin) throws Exception {
        long workspace = admin.getUserGroupId();
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, workspace);
        mvc.perform(post("/payers/" + payer + "/portals").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());
        long template = jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class, workspace);
        portals.saveVersion(workspace, template, null, List.of(
                new PortalField("First name", "label", "First name", "text", "provider.first_name", "", "", ""),
                new PortalField("Accepting new patients", "label", "Accepting new patients", "checkbox", "", "", "Yes", "")),
                admin.getEmail());
        return template;
    }

    private long claimedJob(User admin) {
        long workspace = admin.getUserGroupId();
        long provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, workspace);
        long payer = jdbc.queryForObject("SELECT min(id) FROM payers WHERE user_group_id = ?", Long.class, workspace);
        long template = jdbc.queryForObject("""
                        INSERT INTO portal_templates (user_group_id, payer_id, name, start_url, created_by)
                        VALUES (?, ?, 'Enrollment', 'https://portal.example.com', ?) RETURNING id""",
                Long.class, workspace, payer, admin.getEmail());
        return jdbc.queryForObject("""
                        INSERT INTO runner_jobs (user_group_id, user_id, kind, template_id, template_revision, provider_id,
                                                 status, claimed_at, created_by)
                        VALUES (?, ?, 'fill', ?, 1, ?, 'claimed', now(), ?) RETURNING id""",
                Long.class, workspace, admin.getId(), template, provider, admin.getEmail());
    }

    private User practice() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Live fill " + UUID.randomUUID());
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
