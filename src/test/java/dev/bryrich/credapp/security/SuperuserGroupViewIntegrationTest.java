package dev.bryrich.credapp.security;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderRepository;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.ssn.SsnAccessLogRepository;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A superuser across user groups, and the read-only view of another group, end to end. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SuperuserGroupViewIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired UserGroupRepository userGroups;
    @Autowired ProviderRepository providers;
    @Autowired SsnAccessLogRepository audit;

    private User superuser;
    private User otherAdmin;
    private Long otherGroup;
    private Long otherProvider;
    /** Spring Session keeps the real session in the database, keyed by this cookie. */
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        superuser = users.promoteToSuperuser(register("Super workspace").getId());
        otherAdmin = register("Other practice");
        otherGroup = otherAdmin.getUserGroupId();
        as(superuser, () -> providers.saveAndFlush(new Provider("Mine", "SuperWorkspace")));
        otherProvider = as(otherAdmin, () -> {
            Provider provider = new Provider("Theirs", "OtherWorkspace");
            provider.setSsn("123456789");
            return providers.saveAndFlush(provider).getId();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theUsersPageListsEveryGroupsAccountsWithAWayIntoEachGroup() throws Exception {
        // The accounts list is paged and other tests add accounts, so this checks the
        // unpaged group card here and the accounts through the group filter below.
        perform(get("/admin/users").with(signedIn(superuser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Other practice")))
                .andExpect(content().string(containsString("/admin/user-groups/" + otherGroup + "/view")));

        perform(get("/admin/users").param("group", otherGroup.toString()).with(signedIn(superuser)))
                .andExpect(content().string(containsString(otherAdmin.getEmail())))
                .andExpect(content().string(not(containsString("<span>" + superuser.getEmail() + "</span>"))));
    }

    @Test
    void anAdminGetsNoneOfIt() throws Exception {
        perform(get("/admin/users").param("group", superuser.getUserGroupId().toString())
                        .with(signedIn(otherAdmin)))
                .andExpect(content().string(not(containsString(superuser.getEmail()))))
                .andExpect(content().string(not(containsString("/admin/user-groups/"))));
        perform(post("/admin/user-groups/" + superuser.getUserGroupId() + "/view")
                        .with(signedIn(otherAdmin)).with(csrf()).with(inSession()))
                .andExpect(status().isForbidden());
        perform(get("/providers").with(signedIn(otherAdmin)).with(inSession()))
                .andExpect(content().string(not(containsString("SuperWorkspace"))));
    }

    @Test
    void viewingAnotherGroupShowsItsRecordsAndChangesNothing() throws Exception {
        perform(post("/admin/user-groups/" + otherGroup + "/view")
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/providers"));

        perform(get("/providers").with(signedIn(superuser)).with(inSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OtherWorkspace")))
                .andExpect(content().string(not(containsString("SuperWorkspace"))))
                .andExpect(content().string(containsString("Back to my group")))
                .andExpect(content().string(not(containsString("/providers/new"))));
        perform(get("/providers/" + otherProvider).with(signedIn(superuser)).with(inSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/providers/" + otherProvider + "/edit"))));

        perform(get("/providers/new").with(signedIn(superuser)).with(inSession()))
                .andExpect(redirectedUrl("/providers"));
        perform(post("/providers").with(signedIn(superuser)).with(csrf()).with(inSession())
                        .param("details.firstName", "Sneaky").param("details.lastName", "Insert"))
                .andExpect(status().isForbidden());
        perform(post("/providers/" + otherProvider + "/delete")
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(status().isForbidden());
        perform(post("/admin/users/" + otherAdmin.getId() + "/enabled").param("enabled", "false")
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(status().isForbidden());

        perform(post("/providers/" + otherProvider + "/ssn")
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssn").value("123456789"));

        perform(get("/admin/users").with(signedIn(superuser)).with(inSession()))
                .andExpect(content().string(containsString(otherAdmin.getEmail())))
                .andExpect(content().string(not(containsString("<span>" + superuser.getEmail() + "</span>"))));

        perform(post("/admin/user-groups/view/exit").with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/admin/users"));
        perform(get("/providers").with(signedIn(superuser)).with(inSession()))
                .andExpect(content().string(containsString("SuperWorkspace")))
                .andExpect(content().string(not(containsString("Back to my group"))));

        as(otherAdmin, () -> {
            assertThat(providers.findAll()).extracting(Provider::getLastName).containsExactly("OtherWorkspace");
            assertThat(users.findById(otherAdmin.getId()).isEnabled()).isTrue();
            assertThat(audit.findAll()).singleElement().satisfies(log -> {
                assertThat(log.getUserId()).isEqualTo(superuser.getId());
                assertThat(log.getUserGroupId()).isEqualTo(otherGroup);
            });
            return null;
        });
    }

    @Test
    void aSuperuserCanAddAnAccountToAnyGroupButAnAdminOnlyToTheirOwn() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        perform(post("/admin/users").with(signedIn(superuser)).with(csrf())
                        .param("email", email).param("password", PASSWORD).param("role", "COORDINATOR")
                        .param("userGroupId", otherGroup.toString()))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findByEmail(email).orElseThrow().getUserGroupId()).isEqualTo(otherGroup);

        String sneaky = UUID.randomUUID() + "@example.com";
        perform(post("/admin/users").with(signedIn(otherAdmin)).with(csrf())
                        .param("email", sneaky).param("password", PASSWORD).param("role", "COORDINATOR")
                        .param("userGroupId", superuser.getUserGroupId().toString()))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findByEmail(sneaky).orElseThrow().getUserGroupId()).isEqualTo(otherGroup);
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

    /** Sends the session cookie that an earlier request in this test was given. */
    private RequestPostProcessor inSession() {
        return request -> {
            if (sessionCookie != null) {
                request.setCookies(sessionCookie);
            }
            return request;
        };
    }

    /** MockMvc with the session cookie kept whenever the app sets one. */
    private ResultActions perform(RequestBuilder request) throws Exception {
        ResultActions result = mvc.perform(request);
        Cookie issued = result.andReturn().getResponse().getCookie("SESSION");
        if (issued != null) {
            sessionCookie = issued;
        }
        return result;
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }

    private <T> T as(User actor, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new CredAppUserDetails(actor);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
