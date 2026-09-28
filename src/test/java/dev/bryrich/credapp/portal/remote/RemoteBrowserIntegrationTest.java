package dev.bryrich.credapp.portal.remote;

import dev.bryrich.credapp.TestcontainersConfiguration;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opening a portal in CredCloud's browser, and nobody but its owner reaching it. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.remote-browser.max-sessions=2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RemoteBrowserIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired RemoteBrowsers browsers;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void closeAll() throws Exception {
        browsers.closeAll();
    }

    @Test
    void onlyItsOwnerCanSeeOrUseIt() throws Exception {
        User owner = practice();
        User stranger = practice();
        long template = template(owner);

        String page = mvc.perform(post("/portal-templates/" + template + "/live").with(signedIn(owner)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(page).matches("/live/[A-Za-z0-9_-]{32}");

        mvc.perform(get(page).with(signedIn(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(" / Enrollment")));
        mvc.perform(get(page + "/stream").with(signedIn(owner)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());
        mvc.perform(post(page + "/input").with(signedIn(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"type\":\"move\",\"x\":10,\"y\":10}]"))
                .andExpect(status().isNoContent());
        // Clicks and keys need the page's CSRF token.
        mvc.perform(post(page + "/input").with(signedIn(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());

        mvc.perform(get(page).with(signedIn(stranger))).andExpect(redirectedUrl("/"));
        mvc.perform(get(page + "/stream").with(signedIn(stranger))).andExpect(status().isNotFound());
        mvc.perform(post(page + "/input").with(signedIn(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"type\":\"key\",\"key\":\"Enter\"}]"))
                .andExpect(status().isNotFound());
        mvc.perform(post(page + "/close").with(signedIn(stranger)).with(csrf()));
        mvc.perform(post("/portal-templates/" + template + "/live").with(signedIn(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
        // A stranger can't close it either.
        mvc.perform(get(page).with(signedIn(owner))).andExpect(status().isOk());

        mvc.perform(post(page + "/close").with(signedIn(owner)).with(csrf()))
                .andExpect(redirectedUrl("/payers/" + payer(owner) + "/portals"));
        mvc.perform(get(page).with(signedIn(owner))).andExpect(redirectedUrl("/"));
    }

    @Test
    void oneBrowserEachAndNoMoreThanTheServerAllows() throws Exception {
        User first = practice();
        User second = practice();
        User third = practice();

        String old = open(first);
        String replaced = open(first);
        assertThat(replaced).isNotEqualTo(old);
        // Opening another closes her first.
        mvc.perform(get(old).with(signedIn(first))).andExpect(redirectedUrl("/"));

        open(second);
        mvc.perform(post("/portal-templates/" + template(third) + "/live").with(signedIn(third)).with(csrf()))
                .andExpect(redirectedUrl("/payers/" + payer(third) + "/portals"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                        .attribute("error", org.hamcrest.Matchers.containsString("being used by someone else")));
    }

    private String open(User account) throws Exception {
        return mvc.perform(post("/portal-templates/" + template(account) + "/live").with(signedIn(account)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    }

    private long template(User account) throws Exception {
        Long existing = jdbc.query("SELECT min(id) FROM portal_templates WHERE user_group_id = ?",
                (r, i) -> r.getObject(1, Long.class), account.getUserGroupId()).getFirst();
        if (existing != null) {
            return existing;
        }
        mvc.perform(post("/payers/" + payer(account) + "/portals").with(signedIn(account)).with(csrf())
                        .param("name", "Enrollment").param("startUrl", "https://portal.example.com/enroll"))
                .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT id FROM portal_templates WHERE user_group_id = ?", Long.class,
                account.getUserGroupId());
    }

    private long payer(User account) {
        Long payer = jdbc.query("SELECT min(id) FROM payers WHERE user_group_id = ?",
                (r, i) -> r.getObject(1, Long.class), account.getUserGroupId()).getFirst();
        if (payer != null) {
            return payer;
        }
        return jdbc.queryForObject("INSERT INTO payers (user_group_id, name) VALUES (?, 'Aetna') RETURNING id",
                Long.class, account.getUserGroupId());
    }

    private User practice() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Live " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
