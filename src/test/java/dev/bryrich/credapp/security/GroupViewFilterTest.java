package dev.bryrich.credapp.security;

import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class GroupViewFilterTest {

    private final GroupViewFilter filter = new GroupViewFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pagesStillLoadWhileViewing() throws Exception {
        MockHttpServletResponse response = run(viewing("GET", "/providers/5"), Role.SUPERUSER);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void writesAreRefusedWhileViewing() throws Exception {
        MockHttpServletResponse response = run(viewing("POST", "/providers/5/edit"), Role.SUPERUSER);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getErrorMessage()).contains("Other Practice").contains("read-only");
    }

    @Test
    void editScreensSendYouBackToTheReadablePage() throws Exception {
        assertThat(run(viewing("GET", "/providers/5/edit"), Role.SUPERUSER).getRedirectedUrl())
                .isEqualTo("/providers/5");
        assertThat(run(viewing("GET", "/providers/new"), Role.SUPERUSER).getRedirectedUrl())
                .isEqualTo("/providers");
        assertThat(run(viewing("GET", "/payers/1/contacts/2/edit"), Role.SUPERUSER).getRedirectedUrl())
                .isEqualTo("/payers");
        assertThat(run(viewing("GET", "/admin/users/new"), Role.SUPERUSER).getRedirectedUrl())
                .isEqualTo("/admin/users");
        MockHttpServletRequest legacyEdit = viewing("GET", "/owners/3");
        legacyEdit.setParameter("edit", "1");
        assertThat(run(legacyEdit, Role.SUPERUSER).getRedirectedUrl()).isEqualTo("/owners/3");
    }

    @Test
    void leavingSwitchingSigningOutAndSsnRevealsStillWork() throws Exception {
        for (String path : new String[]{"/admin/user-groups/view/exit", "/admin/user-groups/9/view",
                "/logout", "/providers/5/ssn", "/owners/5/ssn"}) {
            assertThat(run(viewing("POST", path), Role.SUPERUSER).getStatus()).as(path).isEqualTo(200);
        }
    }

    @Test
    void aViewLeftOverFromBeforeADemotionIsDropped() throws Exception {
        MockHttpServletRequest request = viewing("POST", "/providers");

        MockHttpServletResponse response = run(request, Role.ADMIN);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(ViewedGroup.id(request)).isNull();
    }

    private static MockHttpServletRequest viewing(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        UserGroup group = new UserGroup("Other Practice");
        ReflectionTestUtils.setField(group, "id", 9L);
        ViewedGroup.start(request.getSession(), group);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, Role role) throws Exception {
        User user = new User("someone@example.com", "hash", 1L);
        user.setRole(role);
        CredAppUserDetails principal = new CredAppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
