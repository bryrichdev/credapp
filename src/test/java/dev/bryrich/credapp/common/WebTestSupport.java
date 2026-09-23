package dev.bryrich.credapp.common;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Shared fixtures for the web controller tests in every feature package. WebModelAdvice reads a CredAppUserDetails
 * off the security context to build the topbar, so a plain @WithMockUser is not enough —
 * the templates would render with a null user.
 */
public final class WebTestSupport {

    private WebTestSupport() {
    }

    public static RequestPostProcessor coordinator() {
        User user = new User("coordinator@credapp.local", "hashed-value", 42L);
        user.setFullName("Casey Coordinator");
        user.setRole(Role.COORDINATOR);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user(new CredAppUserDetails(user));
    }

    public static Provider provider(Long id, String first, String last) {
        Provider provider = new Provider(first, last);
        ReflectionTestUtils.setField(provider, "id", id);
        return provider;
    }
}
