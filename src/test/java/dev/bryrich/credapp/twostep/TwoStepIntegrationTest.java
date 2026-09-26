package dev.bryrich.credapp.twostep;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.mail.Email;
import dev.bryrich.credapp.mail.RecordingEmailSender;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Two-step sign-in and lockouts, through real sign-ins with real sessions. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.mail.async=false",
        "credapp.two-step.required-for-admins=true"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class TwoStepIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TwoStepCipher cipher;
    @Autowired RecordingEmailSender emails;

    private User admin;
    private Browser browser;

    @BeforeEach
    void setUp() {
        admin = registerAdmin();
        browser = new Browser();
    }

    @Test
    void anAdminSetsItUpAtFirstSignInThenNeedsACodeEveryTime() throws Exception {
        browser.signIn(admin.getEmail(), PASSWORD);
        assertThat(browser.get("/providers")).isEqualTo("/account/two-step/setup");
        assertThat(browser.get("/account")).as("not even their account page yet").isEqualTo("/account/two-step/setup");

        MvcResult page = browser.send(get("/account/two-step/setup"));
        assertThat(page.getResponse().getContentAsString()).contains("<span>Admin</span> accounts must use two-step sign-in");
        assertThat(browser.post("/account/two-step/setup", "code", "000000")).isNull(); // shown again with an error
        String next = browser.post("/account/two-step/setup", "code", code(admin, 0));
        assertThat(next).isEqualTo("/account/two-step/codes");
        MvcResult codesPage = browser.lastWithFlash(get("/account/two-step/codes"));
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) codesPage.getModelAndView().getModel().get("codes");
        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(browser.get("/providers")).as("through now").isNull();
        assertThat(subjects(admin)).contains("Two-step sign-in is on for your CredCloud account");

        // Next time: password, then code.
        browser = new Browser();
        browser.signIn(admin.getEmail(), PASSWORD);
        assertThat(browser.get("/providers")).isEqualTo("/login/two-step");
        assertThat(browser.post("/login/two-step", "code", "123456")).isNull();
        assertThat(browser.post("/login/two-step", "code", code(admin, 1))).isEqualTo("/");
        assertThat(browser.get("/providers")).isNull();

        // Or a recovery code, once.
        browser = new Browser();
        browser.signIn(admin.getEmail(), PASSWORD);
        assertThat(browser.post("/login/two-step", "code", codes.getFirst().toUpperCase())).isEqualTo("/");
        assertThat(subjects(admin)).contains("A CredCloud recovery code was used");
        browser = new Browser();
        browser.signIn(admin.getEmail(), PASSWORD);
        assertThat(browser.post("/login/two-step", "code", codes.getFirst())).as("used up").isNull();
    }

    @Test
    void codesArentOptionalJustBecauseSomeoneElseInTheBrowserPassedThem() throws Exception {
        User coordinator = users.createAs(admin, UUID.randomUUID() + "@example.com", PASSWORD, "Co Ordinator",
                Role.COORDINATOR);
        turnOn(admin);
        browser.signIn(coordinator.getEmail(), PASSWORD);
        assertThat(browser.get("/providers")).as("two-step is optional for coordinators").isNull();

        // Signing in as the admin in the same browser still asks for the admin's code.
        browser.signIn(admin.getEmail(), PASSWORD);
        assertThat(browser.get("/providers")).isEqualTo("/login/two-step");
    }

    @Test
    void fiveWrongCodesLockTheAccountAndSignOut() throws Exception {
        turnOn(admin);
        browser.signIn(admin.getEmail(), PASSWORD);
        for (int i = 0; i < 4; i++) {
            assertThat(browser.post("/login/two-step", "code", "000000")).isNull();
        }
        assertThat(browser.post("/login/two-step", "code", "000000")).isEqualTo("/login?locked");
        assertThat(browser.get("/providers")).endsWith("/login");
        assertThat(new Browser().signIn(admin.getEmail(), PASSWORD)).isEqualTo("/login?locked");
        assertThat(subjects(admin)).contains("Your CredCloud account was locked");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountUntilAnAdminUnlocksIt() throws Exception {
        User coordinator = users.createAs(admin, UUID.randomUUID() + "@example.com", PASSWORD, "Co Ordinator",
                Role.COORDINATOR);
        for (int i = 0; i < 4; i++) {
            assertThat(new Browser().signIn(coordinator.getEmail(), "wrong-password-123")).isEqualTo("/login?error");
        }
        assertThat(new Browser().signIn(coordinator.getEmail(), PASSWORD)).as("four don't lock").isEqualTo("/");
        for (int i = 0; i < 5; i++) {
            new Browser().signIn(coordinator.getEmail(), "wrong-password-123");
        }
        assertThat(new Browser().signIn(coordinator.getEmail(), PASSWORD)).isEqualTo("/login?locked");

        turnOn(admin);
        browser.signIn(admin.getEmail(), PASSWORD);
        browser.post("/login/two-step", "code", code(admin, 1));
        assertThat(browser.post("/admin/users/" + coordinator.getId() + "/unlock")).isEqualTo("/admin/users");
        assertThat(new Browser().signIn(coordinator.getEmail(), PASSWORD)).isEqualTo("/");
    }

    @Test
    void aSuperuserCanResetAnAdminsButNobodyCanTurnOffTheirOwn() throws Exception {
        // Admins manage coordinators; an admin's own two-step is reset by a superuser.
        admin = users.promoteToSuperuser(admin.getId());
        User second = users.createAs(admin, UUID.randomUUID() + "@example.com", PASSWORD, "Second Admin", Role.ADMIN);
        turnOn(admin);
        turnOn(second);

        browser.signIn(admin.getEmail(), PASSWORD);
        browser.post("/login/two-step", "code", code(admin, 1));
        assertThat(browser.post("/account/two-step/off", "currentPassword", PASSWORD)).isEqualTo("/account#two-step");
        assertThat(isOn(admin)).as("admins can't turn their own off").isTrue();
        assertThat(browser.post("/admin/users/" + admin.getId() + "/two-step/reset")).isEqualTo("/admin/users");
        assertThat(isOn(admin)).as("nor reset their own from the Users page").isTrue();

        assertThat(browser.post("/admin/users/" + second.getId() + "/two-step/reset")).isEqualTo("/admin/users");
        assertThat(isOn(second)).isFalse();
        assertThat(subjects(second)).contains("Two-step sign-in was turned off for your CredCloud account");
        Browser theirs = new Browser();
        theirs.signIn(second.getEmail(), PASSWORD);
        assertThat(theirs.get("/providers")).as("set it up again").isEqualTo("/account/two-step/setup");
    }

    // ---------- helpers ----------

    /** Sets two-step up for an account directly, as the setup page would. */
    private void turnOn(User account) {
        byte[] secret = Totp.newSecret();
        jdbc.update("INSERT INTO user_two_step (user_id, secret, enabled_at, last_step) VALUES (?, ?, now(), 0)",
                account.getId(), cipher.encrypt(account.getId(), secret));
    }

    private boolean isOn(User account) {
        return jdbc.queryForObject("SELECT count(*) FROM user_two_step WHERE user_id = ? AND enabled_at IS NOT NULL",
                Integer.class, account.getId()) == 1;
    }

    /** The app's code for now plus some steps, from the stored secret. */
    private String code(User account, int stepsAhead) {
        byte[] stored = jdbc.queryForObject("SELECT secret FROM user_two_step WHERE user_id = ?", byte[].class,
                account.getId());
        return Totp.code(cipher.decrypt(account.getId(), stored), Totp.step(Instant.now()) + stepsAhead);
    }

    private List<String> subjects(User account) {
        return emails.to(account.getEmail()).stream().map(Email::subject).toList();
    }

    private User registerAdmin() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Two-step " + UUID.randomUUID());
        return registration.register(form);
    }

    /** One browser: keeps its session cookie from response to response. */
    private class Browser {
        private Cookie session;
        private MvcResult last;

        /** @return where the sign-in sent the browser */
        String signIn(String email, String password) throws Exception {
            return post("/login", "username", email, "password", password);
        }

        /** @return where it redirected, or null for a page shown */
        String get(String path) throws Exception {
            return location(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)));
        }

        String post(String path, String... params) throws Exception {
            MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post(path).with(csrf());
            for (int i = 0; i < params.length; i += 2) {
                request.param(params[i], params[i + 1]);
            }
            return location(send(request));
        }

        /** The next request, carrying the flash attributes the last redirect set. */
        MvcResult lastWithFlash(MockHttpServletRequestBuilder request) throws Exception {
            return send(request.flashAttrs(last.getFlashMap()));
        }

        MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
            if (session != null) {
                request.cookie(session);
            }
            MvcResult result = mvc.perform((RequestBuilder) request).andReturn();
            Cookie issued = result.getResponse().getCookie("SESSION");
            if (issued != null) {
                session = issued.getMaxAge() == 0 ? null : issued;
            }
            last = result;
            return result;
        }

        private static String location(MvcResult result) {
            String location = result.getResponse().getRedirectedUrl();
            return location == null ? null : location.replaceFirst("^http://localhost", "");
        }
    }
}
