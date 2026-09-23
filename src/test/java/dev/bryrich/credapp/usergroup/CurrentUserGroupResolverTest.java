package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserGroupResolverTest {
    private final CurrentUserGroupResolver resolver = new CurrentUserGroupResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aSuperuserViewingAnotherGroupReadsThatGroup() {
        User superuser = new User("super@example.com", "hash", 123L);
        superuser.setRole(Role.SUPERUSER);
        authenticate(superuser);
        viewing(456L);

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(456L);
    }

    @Test
    void anyoneElseIgnoresAViewedGroupInTheirSession() {
        User admin = new User("admin@example.com", "hash", 123L);
        admin.setRole(Role.ADMIN);
        authenticate(admin);
        viewing(456L);

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(123L);
    }

    private static void viewing(Long groupId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserGroup group = new UserGroup("Viewed");
        ReflectionTestUtils.setField(group, "id", groupId);
        ViewedGroup.start(request.getSession(), group);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void backgroundWorkAndPublicRequestsNeverInheritAGroup() {
        SecurityContextHolder.clearContext();
        assertThat(resolver.resolveCurrentTenantIdentifier()).isZero();
    }

    @Test
    void usesOnlyTheAuthenticatedAccountsExplicitGroup() {
        User user = new User("someone@example.com", "hash", 123L);
        authenticate(user);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(123L);
        user.setEnabled(false);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isZero();
    }

    @Test
    void aLegacySessionWithoutGroupMembershipHasNoAccess() {
        User user = new User("someone@example.com", "hash", 123L);
        ReflectionTestUtils.setField(user, "userGroupId", null);
        authenticate(user);
        assertThat(user.isEnabled()).isFalse();
        assertThat(resolver.resolveCurrentTenantIdentifier()).isZero();
    }

    private void authenticate(User user) {
        CredAppUserDetails principal = new CredAppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
