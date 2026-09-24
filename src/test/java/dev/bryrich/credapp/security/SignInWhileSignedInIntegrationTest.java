package dev.bryrich.credapp.security;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signing in, switching accounts and registering while already signed in, and forms left open too long. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignInWhileSignedInIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;

    @Test
    void aSignedInUserCanRegisterAnotherAccount() throws Exception {
        User current = register("Current " + UUID.randomUUID());
        String email = UUID.randomUUID() + "@example.com";

        mvc.perform(get("/register").with(signedIn(current)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("signed in as <strong>" + current.getEmail())));
        mvc.perform(post("/register").with(signedIn(current)).with(csrf())
                        .param("email", email).param("fullName", "Second Account").param("role", "ADMIN")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                        .param("groupName", "Second " + UUID.randomUUID()))
                .andExpect(redirectedUrl("/login"));

        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void signingInAsSomeoneElseSwitchesAccountsAndEndsTheGroupView() throws Exception {
        User first = users.promoteToSuperuser(register("First " + UUID.randomUUID()).getId());
        User second = users.promoteToSuperuser(register("Second " + UUID.randomUUID()).getId());
        User viewed = register("Viewed " + UUID.randomUUID());

        Cookie session = signIn(first, null);
        mvc.perform(post("/admin/user-groups/" + viewed.getUserGroupId() + "/view").cookie(session).with(csrf()))
                .andExpect(redirectedUrl("/providers"));
        mvc.perform(get("/admin/users").cookie(session))
                .andExpect(content().string(containsString("viewing-banner")));
        mvc.perform(get("/login").cookie(session))
                .andExpect(content().string(containsString("Signing in here switches to the other account")));

        Cookie switched = signIn(second, session);
        mvc.perform(get("/admin/users").cookie(switched))
                .andExpect(content().string(containsString(second.getEmail())))
                .andExpect(content().string(not(containsString("viewing-banner"))));

        mvc.perform(get("/login")).andExpect(content().string(not(containsString("signed in as"))));
    }

    @Test
    void aSignInOrRegisterPageThatExpiredIsShownAgainInsteadOfA403() throws Exception {
        mvc.perform(post("/login").with(csrf().useInvalidToken())
                        .param("username", "someone@example.com").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/login").param("expired", ""))
                .andExpect(content().string(containsString("That page had expired")));

        mvc.perform(post("/register").with(csrf().useInvalidToken()).param("email", "x@example.com"))
                .andExpect(redirectedUrl("/register?expired"));
        mvc.perform(get("/register").param("expired", ""))
                .andExpect(content().string(containsString("That page had expired")));

        // A form left open past the session timeout goes to sign-in too.
        mvc.perform(post("/providers").with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void aBadTokenFromSomeoneSignedInIsStillRefused() throws Exception {
        User current = register("Signed in " + UUID.randomUUID());
        mvc.perform(post("/providers").with(signedIn(current)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/providers").with(signedIn(current)))
                .andExpect(status().isForbidden());
    }

    private User register(String groupName) {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test User");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName(groupName);
        return registration.register(form);
    }

    /** Signs in through the real form, from an existing session if given, and returns the session cookie. */
    private Cookie signIn(User account, Cookie existing) throws Exception {
        var request = post("/login").with(csrf()).param("username", account.getEmail()).param("password", PASSWORD);
        if (existing != null) {
            request.cookie(existing);
        }
        var response = mvc.perform(request).andExpect(redirectedUrl("/")).andReturn().getResponse();
        Cookie issued = response.getCookie("SESSION");
        return issued != null ? issued : existing;
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
