package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.entity.enums.Role;
import dev.bryrich.credapp.security.CredAppUserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Shared fixtures for the web controller tests. WebModelAdvice reads a CredAppUserDetails
 * off the security context to build the topbar, so a plain @WithMockUser is not enough —
 * the templates would render with a null user.
 */
final class WebTestSupport {

    private WebTestSupport() {
    }

    static RequestPostProcessor coordinator() {
        User user = new User("coordinator@credapp.local", "hashed-value");
        user.setFullName("Casey Coordinator");
        user.setRole(Role.COORDINATOR);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user(new CredAppUserDetails(user));
    }

    static Provider provider(Long id, String first, String last) {
        Provider provider = new Provider(first, last);
        ReflectionTestUtils.setField(provider, "id", id);
        return provider;
    }
}
