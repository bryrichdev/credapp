package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.TestcontainersConfiguration;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

/** Teaching a portal in CredCloud's own browser: adding it, the boxes, saving, and who can. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.remote-browser.max-sessions=2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LiveTeachIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final String FIRST_NAME = """
            {"label":"First name","by":"label","locator":"First name *","kind":"text","page":"/enroll",
             "source":"provider.first_name","format":"AS_SAVED","defaultValue":""}""";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired RemoteBrowsers browsers;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void closeAll() throws Exception {
        browsers.closeAll();
    }

    @Test
    void addAPortalTeachItAndSaveAVersion() throws Exception {
        User admin = practice();
        User stranger = practice();
        long workspace = admin.getUserGroupId();
        long payer = payer(admin);

        String page = mvc.perform(post("/payers/" + payer + "/portals/live").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(page).matches("/live/[A-Za-z0-9_-]{32}");
        long template = jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class, workspace);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runner_jobs WHERE user_group_id = ?", Integer.class, workspace))
                .as("no job waits for the extension").isZero();

        mvc.perform(get(page).with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teaching CredCloud this portal")))
                .andExpect(content().string(containsString("Provider / First name")));
        mvc.perform(get(page + "/teach").with(signedIn(admin)))
                .andExpect(jsonPath("$.boxes.length()").value(0));

        // The browser is on Chromium's error page (the portal can't be reached from the test).
        mvc.perform(post(page + "/pick").with(signedIn(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":100,\"y\":100}"))
                .andExpect(jsonPath("$.problem", containsString("isn't on the portal you're teaching")));

        mvc.perform(post(page + "/teach/add").with(signedIn(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(FIRST_NAME.replace("provider.first_name", "provider.password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Choose listed data")));
        mvc.perform(post(page + "/teach/add").with(signedIn(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(FIRST_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boxes[0].label").value("First name"))
                .andExpect(jsonPath("$.boxes[0].detail").value("Provider / First name"))
                .andExpect(jsonPath("$.unsaved").value(true));

        mvc.perform(post(page + "/teach/add").with(signedIn(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(FIRST_NAME))
                .andExpect(status().isNotFound());
        mvc.perform(post(page + "/teach/add").with(signedIn(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(FIRST_NAME))
                .andExpect(status().isForbidden());

        mvc.perform(post(page + "/teach/save").with(signedIn(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Saved as version 1")))
                .andExpect(jsonPath("$.unsaved").value(false));
        assertThat(jdbc.queryForObject("SELECT revision FROM portal_templates WHERE id = ?", Integer.class, template)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                        SELECT fields->0->>'locator' FROM portal_template_versions WHERE template_id = ? AND revision = 1""",
                String.class, template)).isEqualTo("First name *");

        mvc.perform(post(page + "/teach/remove").param("index", "0").with(signedIn(admin)).with(csrf()))
                .andExpect(jsonPath("$.boxes.length()").value(0))
                .andExpect(jsonPath("$.unsaved").value(true));
        mvc.perform(post(page + "/close").with(signedIn(admin)).with(csrf()))
                .andExpect(redirectedUrl("/payers/" + payer + "/portals"))
                .andExpect(flash().attribute("message", containsString("without saving")));
    }

    @Test
    void teachingAgainStartsFromTheSavedBoxes() throws Exception {
        User admin = practice();
        long payer = payer(admin);
        String first = mvc.perform(post("/payers/" + payer + "/portals/live").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(first + "/teach/add").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(FIRST_NAME)).andExpect(status().isOk());
        mvc.perform(post(first + "/teach/save").with(signedIn(admin)).with(csrf())).andExpect(status().isOk());
        long template = jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class,
                admin.getUserGroupId());

        String again = mvc.perform(post("/portal-templates/" + template + "/live").with(signedIn(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(again + "/teach").with(signedIn(admin)))
                .andExpect(jsonPath("$.boxes[0].label").value("First name"))
                .andExpect(jsonPath("$.unsaved").value(false));
    }

    @Test
    void aBadAddressStaysOnThePageWithWhatSheTyped() throws Exception {
        User admin = practice();
        long payer = payer(admin);
        mvc.perform(post("/payers/" + payer + "/portals/live").with(signedIn(admin)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "http://portal.example.com"))
                .andExpect(redirectedUrl("/payers/" + payer + "/portals"))
                .andExpect(flash().attribute("error", containsString("https://")))
                .andExpect(flash().attribute("name", "Enrollment"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portal_templates WHERE user_group_id = ?", Integer.class,
                admin.getUserGroupId())).isZero();
    }

    private long payer(User account) {
        Long payer = jdbc.query("SELECT min(id) FROM payers WHERE user_group_id = ?",
                (r, i) -> r.getObject(1, Long.class), account.getUserGroupId()).getFirst();
        return payer != null ? payer : jdbc.queryForObject(
                "INSERT INTO payers (user_group_id, name) VALUES (?, 'Aetna') RETURNING id", Long.class,
                account.getUserGroupId());
    }

    private User practice() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Teach " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
