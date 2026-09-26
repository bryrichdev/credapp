package dev.bryrich.credapp.common;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Short forms opened in a dialog: what the server sends when modal.js asks. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModalFormsIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = register();
        var principal = new CredAppUserDetails(admin);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        imports.importFile(TestWorkbook.fullPractice().bytes());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyFormOpenedInADialogHasSomethingToShowInIt() throws Exception {
        Long group = admin.getUserGroupId();
        Long owner = jdbc.queryForObject("SELECT min(id) FROM owners WHERE user_group_id = ?", Long.class, group);
        Long license = jdbc.queryForObject("SELECT min(id) FROM licenses WHERE user_group_id = ?", Long.class, group);
        Long payer = jdbc.queryForObject("SELECT min(payer_id) FROM payer_contacts WHERE user_group_id = ?", Long.class, group);
        Long contact = jdbc.queryForObject("SELECT min(id) FROM payer_contacts WHERE payer_id = ?", Long.class, payer);
        User coordinator = users.createInGroup(UUID.randomUUID() + "@example.com", PASSWORD, "Casey", Role.COORDINATOR, group);

        for (String path : List.of("/payers/new", "/owners/" + owner + "/groups/new", "/licenses/new",
                "/licenses/" + license + "/edit", "/payers/" + payer + "/contacts/" + contact + "/edit",
                "/admin/tracking-settings", "/admin/users/new", "/admin/users/" + coordinator.getId() + "/edit")) {
            mvc.perform(get(path).header("X-Modal", "1").with(signedIn(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-modal-content")))
                    .andExpect(content().string(containsString("data-modal-title")))
                    .andExpect(content().string(containsString("data-modal-cancel")));
        }
    }

    @Test
    void aSavedFormTellsTheDialogWhereToGoInsteadOfRedirecting() throws Exception {
        mvc.perform(post("/payers").header("X-Modal", "1").with(signedIn(admin)).with(csrf())
                        .param("name", "Modal Health " + UUID.randomUUID()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Modal-Redirect", matchesPattern("/payers/\\d+")))
                .andExpect(header().doesNotExist("Location"));

        // The same request from a plain form is an ordinary redirect.
        mvc.perform(post("/payers").with(signedIn(admin)).with(csrf())
                        .param("name", "Plain Health " + UUID.randomUUID()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/payers/*"));
    }

    @Test
    void aFormWithErrorsComesBackForTheDialogToShowAgain() throws Exception {
        mvc.perform(post("/admin/tracking-settings").header("X-Modal", "1").with(signedIn(admin)).with(csrf())
                        .param("warningDays", "30").param("urgentDays", "60")
                        .param("stalledDays", "30").param("caqhDays", "120"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-content")))
                .andExpect(content().string(containsString("Due soon has to fit within coming up")));

        mvc.perform(post("/admin/tracking-settings").header("X-Modal", "1").with(signedIn(admin)).with(csrf())
                        .param("warningDays", "30").param("urgentDays", "7")
                        .param("stalledDays", "30").param("caqhDays", "120"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Modal-Redirect", "/tracking"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void signedOutTheDialogIsSentToSignIn() throws Exception {
        mvc.perform(get("/payers/new").header("X-Modal", "1"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Modal-Redirect", containsString("/login")));
    }

    /** A link marked data-modal to a page without data-modal-content would just reload as a page. */
    @Test
    void theModalScriptIsLoadedOnEverySignedInPage() throws IOException {
        String layout = Files.readString(Path.of("src/main/resources/templates/fragments/layout.html"));
        assertThat(layout).contains("/js/modal.js");
        Pattern link = Pattern.compile("<a[^>]*\\bdata-modal\\b[^>]*>", Pattern.DOTALL);
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            long links = files.filter(file -> file.toString().endsWith(".html"))
                    .mapToLong(file -> {
                        try {
                            Matcher matcher = link.matcher(Files.readString(file));
                            long count = 0;
                            while (matcher.find()) {
                                count++;
                            }
                            return count;
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    }).sum();
            assertThat(links).as("links opening a dialog").isGreaterThanOrEqualTo(9);
        }
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Modal " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
