package dev.bryrich.credapp.passwordreset;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.mail.Email;
import dev.bryrich.credapp.mail.RecordingEmailSender;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.MembershipStatus;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserRepository;
import dev.bryrich.credapp.user.UserService;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Join approval emails and forgotten passwords, end to end. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.mail.async=false",
        "credapp.base-url=https://credcloud.test"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class PasswordResetIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final String NEW_PASSWORD = "brand-new-password-5678";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired UserGroupRepository userGroups;
    @Autowired RecordingEmailSender emails;
    @Autowired JdbcTemplate jdbc;

    private User admin;
    private User coordinator;

    @BeforeEach
    void setUp() {
        admin = register(Role.ADMIN, "Clinic " + UUID.randomUUID(), null);
        String joinCode = userGroups.findById(admin.getUserGroupId()).orElseThrow().getJoinCode();
        coordinator = register(Role.COORDINATOR, null, joinCode);
    }

    @Test
    void approvingAJoinRequestEmailsThePerson() throws Exception {
        assertThat(coordinator.getMembershipStatus()).isEqualTo(MembershipStatus.PENDING);
        mvc.perform(post("/admin/users/" + coordinator.getId() + "/approval").with(signedIn(admin)).with(csrf())
                        .param("approve", "true"))
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(emails.to(coordinator.getEmail())).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("Your CredCloud account is approved");
            assertThat(email.text()).contains("Admin Person approved your request to join")
                    .contains("https://credcloud.test/login");
        });
    }

    @Test
    void aCoordinatorsResetWaitsForTheirAdminThenTheLinkSetsANewPassword() throws Exception {
        approve(coordinator);

        mvc.perform(post("/password-reset").with(csrf()).param("email", coordinator.getEmail().toUpperCase()))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", PasswordResetController.REQUESTED));
        assertThat(emails.resetToken(coordinator.getEmail())).as("no link before approval").isEmpty();
        assertThat(emails.to(admin.getEmail())).singleElement().satisfies(email -> {
            assertThat(email.subject()).contains("asked to reset their CredCloud password");
            assertThat(email.text()).contains("https://credcloud.test/admin/users");
        });

        // Asking again while it waits doesn't pester the admin.
        mvc.perform(post("/password-reset").with(csrf()).param("email", coordinator.getEmail()));
        assertThat(emails.to(admin.getEmail())).hasSize(1);

        mvc.perform(get("/").with(signedIn(admin)))
                .andExpect(content().string(containsString("1 person has asked to reset their password.")));
        long requestId = jdbc.queryForObject(
                "SELECT id FROM password_reset_requests WHERE user_id = ? AND status = 'PENDING'",
                Long.class, coordinator.getId());
        mvc.perform(get("/admin/users").with(signedIn(admin)))
                .andExpect(content().string(containsString("Password reset requests")))
                .andExpect(content().string(containsString("/admin/users/password-resets/" + requestId)));

        mvc.perform(post("/admin/users/password-resets/" + requestId).with(signedIn(admin)).with(csrf())
                        .param("approve", "true"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("message",
                        "Reset link emailed to " + coordinator.getEmail() + ". It works once, for 24 hours."));
        String token = emails.resetToken(coordinator.getEmail()).orElseThrow();
        assertThat(emails.to(coordinator.getEmail()).getLast().text())
                .contains("https://credcloud.test/password-reset/" + token).contains("expires in 24 hours");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_requests WHERE id = ?",
                String.class, requestId)).as("only the hash is stored").isNotEqualTo(token).hasSize(64);

        mvc.perform(get("/password-reset/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(coordinator.getEmail())));
        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", "something-else-entirely"))
                .andExpect(content().string(containsString("The passwords don&#39;t match")));
        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", "short").param("confirmPassword", "short"))
                .andExpect(content().string(containsString("at least 12 characters")));
        assertThat(users.passwordMatches(coordinator.getId(), PASSWORD)).isTrue();

        signedInSession(coordinator.getEmail());
        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", "Your password is changed. Sign in with the new one."));

        assertThat(users.passwordMatches(coordinator.getId(), NEW_PASSWORD)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE lower(principal_name) = ?",
                Long.class, coordinator.getEmail())).as("signed out everywhere").isZero();
        assertThat(emails.to(coordinator.getEmail()).getLast().subject()).isEqualTo("Your CredCloud password was changed");

        // A link works once.
        mvc.perform(get("/password-reset/" + token)).andExpect(content().string(containsString("This link doesn")));
        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", "yet-another-password-9").param("confirmPassword", "yet-another-password-9"))
                .andExpect(content().string(containsString("This link doesn")));
        assertThat(users.passwordMatches(coordinator.getId(), NEW_PASSWORD)).isTrue();
    }

    @Test
    void anAdminGetsTheLinkStraightAwayButNotTwiceInAFewMinutes() throws Exception {
        mvc.perform(post("/password-reset").with(csrf()).param("email", admin.getEmail()))
                .andExpect(redirectedUrl("/login"));
        String token = emails.resetToken(admin.getEmail()).orElseThrow();
        assertThat(emails.to(admin.getEmail()).getLast().text()).contains("expires in 1 hour");

        mvc.perform(post("/password-reset").with(csrf()).param("email", admin.getEmail()));
        assertThat(emails.to(admin.getEmail())).hasSize(1);

        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login"));
        assertThat(users.passwordMatches(admin.getId(), NEW_PASSWORD)).isTrue();
    }

    @Test
    void theFormSaysTheSameThingWhetherOrNotTheEmailHasAnAccount() throws Exception {
        String nobody = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/password-reset").with(csrf()).param("email", nobody))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", PasswordResetController.REQUESTED));
        assertThat(emails.to(nobody)).isEmpty();

        // Not yet approved to join: nothing to reset, and nobody's told.
        mvc.perform(post("/password-reset").with(csrf()).param("email", coordinator.getEmail()))
                .andExpect(flash().attribute("message", PasswordResetController.REQUESTED));
        assertThat(emails.to(admin.getEmail())).isEmpty();

        mvc.perform(post("/password-reset").with(csrf()).param("email", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the email you sign in with")));
    }

    @Test
    void declinedLapsedAndOtherGroupsRequestsCantBeUsed() throws Exception {
        approve(coordinator);
        User otherAdmin = register(Role.ADMIN, "Elsewhere " + UUID.randomUUID(), null);
        mvc.perform(post("/password-reset").with(csrf()).param("email", coordinator.getEmail()));
        long requestId = jdbc.queryForObject("SELECT id FROM password_reset_requests WHERE user_id = ?",
                Long.class, coordinator.getId());

        // Another group's admin can't see it or decide it, and a coordinator can't reach the page at all.
        mvc.perform(get("/admin/users").with(signedIn(otherAdmin)))
                .andExpect(content().string(not(containsString("Password reset requests"))));
        mvc.perform(post("/admin/users/password-resets/" + requestId).with(signedIn(otherAdmin)).with(csrf())
                        .param("approve", "true"))
                .andExpect(flash().attribute("errorMessage", "You can't reset the password of " + coordinator.getEmail() + "."));
        mvc.perform(post("/admin/users/password-resets/" + requestId).with(signedIn(coordinator)).with(csrf())
                        .param("approve", "true"))
                .andExpect(status().isForbidden());
        assertThat(emails.resetToken(coordinator.getEmail())).isEmpty();

        // A superuser sees every group's requests.
        User superuser = users.promoteToSuperuser(register(Role.ADMIN, "Super " + UUID.randomUUID(), null).getId());
        mvc.perform(get("/admin/users").with(signedIn(superuser)))
                .andExpect(content().string(containsString("/admin/users/password-resets/" + requestId)));

        mvc.perform(post("/admin/users/password-resets/" + requestId).with(signedIn(admin)).with(csrf())
                        .param("approve", "false"))
                .andExpect(flash().attribute("message", "Reset request declined. We've let " + coordinator.getEmail() + " know."));
        assertThat(emails.to(coordinator.getEmail()).getLast().subject()).contains("declined");
        assertThat(emails.resetToken(coordinator.getEmail())).isEmpty();
        mvc.perform(post("/admin/users/password-resets/" + requestId).with(signedIn(admin)).with(csrf())
                        .param("approve", "true"))
                .andExpect(flash().attribute("errorMessage", "That reset request was already handled or has lapsed."));

        // An approved link past its expiry is refused.
        mvc.perform(post("/password-reset").with(csrf()).param("email", coordinator.getEmail()));
        long second = jdbc.queryForObject(
                "SELECT id FROM password_reset_requests WHERE user_id = ? AND status = 'PENDING'", Long.class, coordinator.getId());
        mvc.perform(post("/admin/users/password-resets/" + second).with(signedIn(admin)).with(csrf()).param("approve", "true"));
        String token = emails.resetToken(coordinator.getEmail()).orElseThrow();
        jdbc.update("UPDATE password_reset_requests SET link_expires_at = now() - interval '1 minute' WHERE id = ?", second);
        mvc.perform(get("/password-reset/" + token)).andExpect(content().string(containsString("This link doesn")));

        // So is a link for an account that's since been disabled.
        jdbc.update("UPDATE password_reset_requests SET link_expires_at = now() + interval '1 hour' WHERE id = ?", second);
        users.setEnabledAs(admin, coordinator.getId(), false);
        mvc.perform(post("/password-reset/" + token).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(content().string(containsString("This link doesn")));
        assertThat(users.passwordMatches(coordinator.getId(), PASSWORD)).isTrue();
    }

    @Test
    void theResetPagesAreOpenToEveryoneAndTheSignInPageLinksToThem() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(containsString("href=\"/password-reset\"")));
        mvc.perform(get("/password-reset")).andExpect(status().isOk());
        mvc.perform(get("/password-reset/" + "x".repeat(43))).andExpect(content().string(containsString("This link doesn")));
        mvc.perform(post("/password-reset").with(csrf().useInvalidToken()).param("email", admin.getEmail()))
                .andExpect(redirectedUrl("/password-reset?expired"));
        assertThat(emails.to(admin.getEmail())).isEmpty();
    }

    private void approve(User pending) {
        users.decideJoinRequestAs(admin, pending.getId(), true);
        coordinator = userRepository.findById(pending.getId()).orElseThrow();
    }

    private User register(Role role, String groupName, String joinCode) {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName(role == Role.ADMIN ? "Admin Person" : "Casey Coordinator");
        form.setRole(role);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName(groupName);
        form.setJoinCode(joinCode);
        return registration.register(form);
    }

    private void signedInSession(String principalName) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,
                                            max_inactive_interval, expiry_time, principal_name)
                VALUES (?, ?, ?, ?, 1800, ?, ?)""",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), now, now, now + 1_800_000, principalName);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
