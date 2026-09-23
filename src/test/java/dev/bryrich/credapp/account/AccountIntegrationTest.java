package dev.bryrich.credapp.account;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserRepository;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** My account: every role edits its own account, and nothing saves without the current password. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void aReadOnlyAccountChangesItsOwnNameAndEmailWithItsPassword() throws Exception {
        User reader = users.createAs(admin(), email(), PASSWORD, "Rita Reader", Role.READONLY);
        String newEmail = email();

        mvc.perform(get("/providers").with(signedIn(reader)))
                .andExpect(content().string(containsString("href=\"/account\"")));
        mvc.perform(get("/account").with(signedIn(reader)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rita Reader")))
                .andExpect(content().string(containsString("Read only")));

        mvc.perform(save(reader, "Rita Q. Reader", newEmail, "", "", "not-my-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That isn&#39;t your current password")));
        assertThat(userRepository.findById(reader.getId()).orElseThrow().getEmail()).isEqualTo(reader.getEmail());

        mvc.perform(save(reader, "Rita Q. Reader", newEmail, "", "", ""))
                .andExpect(content().string(containsString("Enter your current password to save changes")));

        mvc.perform(save(reader, "Rita Q. Reader", newEmail.toUpperCase(), "", "", PASSWORD))
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute("message", "Your account is updated."));
        User saved = userRepository.findById(reader.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(newEmail);
        assertThat(saved.getFullName()).isEqualTo("Rita Q. Reader");
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void aNewPasswordFollowsTheRules() throws Exception {
        User coordinator = users.createAs(admin(), email(), PASSWORD, "Cora", Role.COORDINATOR);

        mvc.perform(save(coordinator, "Cora", coordinator.getEmail(), "short", "short", PASSWORD))
                .andExpect(content().string(containsString("Password must be at least 12 characters")));
        mvc.perform(save(coordinator, "Cora", coordinator.getEmail(), NEW_PASSWORD, NEW_PASSWORD + "x", PASSWORD))
                .andExpect(content().string(containsString("The new passwords don&#39;t match")));
        mvc.perform(save(coordinator, "Cora", coordinator.getEmail(), PASSWORD, PASSWORD, PASSWORD))
                .andExpect(content().string(containsString("Choose a password different from your current one")));
        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(coordinator.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void changingThePasswordSignsOutEveryOtherSession() throws Exception {
        User coordinator = users.createAs(admin(), email(), PASSWORD, "Cora", Role.COORDINATOR);
        Cookie laptop = signIn(coordinator.getEmail(), PASSWORD);
        Cookie phone = signIn(coordinator.getEmail(), PASSWORD);
        mvc.perform(get("/account").cookie(laptop)).andExpect(status().isOk());

        MvcResult result = mvc.perform(post("/account").cookie(phone).with(csrf())
                        .param("fullName", "Cora").param("email", coordinator.getEmail())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD)
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/account"))
                .andReturn();
        Cookie phoneAfter = result.getResponse().getCookie("SESSION");
        assertThat(phoneAfter).as("the session id changes").isNotNull();
        assertThat(phoneAfter.getValue()).isNotEqualTo(phone.getValue());

        mvc.perform(get("/account").cookie(phoneAfter)).andExpect(status().isOk());
        mvc.perform(get("/account").cookie(laptop)).andExpect(redirectedUrl("/login"));
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findById(coordinator.getId()).orElseThrow().getPasswordHash())).isTrue();
        signIn(coordinator.getEmail(), NEW_PASSWORD);
    }

    @Test
    void anEmailSomeoneElseHasIsRefused() throws Exception {
        User admin = admin();
        User coordinator = users.createAs(admin, email(), PASSWORD, "Cora", Role.COORDINATOR);

        mvc.perform(save(coordinator, "Cora", admin.getEmail(), "", "", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That email is already in use")));
    }

    @Test
    void adminsEditTheirOwnAccountHereToo() throws Exception {
        User admin = admin();

        mvc.perform(get("/admin/users/" + admin.getId() + "/edit").with(signedIn(admin)))
                .andExpect(redirectedUrl("/account"));
        mvc.perform(post("/admin/users/" + admin.getId() + "/edit").with(signedIn(admin)).with(csrf())
                        .param("email", admin.getEmail()).param("fullName", "Renamed")
                        .param("role", "ADMIN").param("enabled", "true").param("password", NEW_PASSWORD))
                .andExpect(redirectedUrl("/account"));
        User unchanged = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(unchanged.getFullName()).isEqualTo("Test User");
        assertThat(passwordEncoder.matches(PASSWORD, unchanged.getPasswordHash())).isTrue();

        mvc.perform(get("/admin/users").with(signedIn(admin)))
                .andExpect(content().string(containsString("href=\"/account\">Edit</a>")));
        mvc.perform(save(admin, "Ada Admin", admin.getEmail(), "", "", PASSWORD))
                .andExpect(redirectedUrl("/account"));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getFullName()).isEqualTo("Ada Admin");
    }

    @Test
    void aSuperuserViewingAnotherGroupCanStillSaveTheirOwnAccount() throws Exception {
        User superuser = users.promoteToSuperuser(admin().getId());
        Long other = admin().getUserGroupId();
        Cookie session = signIn(superuser.getEmail(), PASSWORD);

        Cookie viewing = cookieAfter(mvc.perform(post("/admin/user-groups/" + other + "/view").cookie(session).with(csrf()))
                .andReturn(), session);
        mvc.perform(post("/account").cookie(viewing).with(csrf())
                        .param("fullName", "Sue Superuser").param("email", superuser.getEmail())
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/account"));
        assertThat(userRepository.findById(superuser.getId()).orElseThrow().getFullName()).isEqualTo("Sue Superuser");
    }

    // ============ helpers ============

    private MockHttpServletRequestBuilder save(User account, String name, String email, String newPassword,
                                               String confirm, String current) {
        return post("/account").with(signedIn(account)).with(csrf())
                .param("fullName", name).param("email", email)
                .param("newPassword", newPassword).param("confirmPassword", confirm)
                .param("currentPassword", current);
    }

    private Cookie signIn(String email, String password) throws Exception {
        MvcResult result = mvc.perform(formLogin("/login").user(email).password(password))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static Cookie cookieAfter(MvcResult result, Cookie previous) {
        Cookie issued = result.getResponse().getCookie("SESSION");
        return issued == null ? previous : issued;
    }

    private User admin() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(email());
        form.setFullName("Test User");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Account test group");
        return registration.register(form);
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
