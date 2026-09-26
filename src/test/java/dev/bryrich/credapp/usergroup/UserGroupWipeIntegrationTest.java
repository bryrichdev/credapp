package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.ImportReport;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

/** Wiping a user group's data, end to end against Postgres. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserGroupWipeIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;

    private User superuser;
    private Long superGroup;
    private User otherAdmin;
    private Long otherGroup;
    private String otherGroupName;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        superuser = users.promoteToSuperuser(register("Super workspace " + UUID.randomUUID()).getId());
        superGroup = superuser.getUserGroupId();
        otherGroupName = "Other practice " + UUID.randomUUID();
        otherAdmin = register(otherGroupName);
        otherGroup = otherAdmin.getUserGroupId();
        fill(superuser);
        fill(otherAdmin);
        jdbc.update("""
                INSERT INTO ssn_access_log (subject_type, subject_id, subject_name, user_id, user_email, user_group_id)
                VALUES ('owner', 1, 'Lee, Ann', ?, ?, ?)""", otherAdmin.getId(), otherAdmin.getEmail(), otherGroup);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyGroupScopedTableIsEitherWipedOrDeliberatelyKept() {
        Set<String> scoped = new HashSet<>(jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE column_name = 'user_group_id' AND table_schema = current_schema()""", String.class));
        Set<String> accountedFor = new HashSet<>(UserGroupWipeService.WIPED.keySet());
        accountedFor.addAll(UserGroupWipeService.KEPT);
        assertThat(scoped).as("a new table with user_group_id needs a place in UserGroupWipeService")
                .containsExactlyInAnyOrderElementsOf(accountedFor);
        assertThat(UserGroupWipeService.DELETE_ORDER)
                .containsExactlyInAnyOrderElementsOf(UserGroupWipeService.WIPED.keySet());
    }

    @Test
    void aWipeEmptiesTheGroupAndKeepsItsAccountsAndEveryoneElsesData() throws Exception {
        Map<String, Long> otherBefore = rows(otherGroup);
        Map<String, Long> superBefore = rows(superGroup);
        assertThat(otherBefore).as("the fixture should reach every table, so the delete order is proven")
                .allSatisfy((table, count) -> assertThat(count).as(table).isPositive());
        long total = otherBefore.values().stream().mapToLong(Long::longValue).sum();

        perform(get("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(otherGroupName)))
                .andExpect(content().string(containsString("Malpractice claims")))
                .andExpect(content().string(containsString("Wipe " + total + " records")));

        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", "  " + otherGroupName + " ").param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("message",
                        "Wiped " + otherGroupName + ": " + total + " records deleted. Its 1 account was kept."));

        assertThat(rows(otherGroup)).allSatisfy((table, count) -> assertThat(count).as(table).isZero());
        assertThat(rows(superGroup)).isEqualTo(superBefore);
        assertThat(users.findById(otherAdmin.getId()).isEnabled()).isTrue();
        assertThat(jdbc.queryForObject("SELECT name FROM user_groups WHERE id = ?", String.class, otherGroup))
                .isEqualTo(otherGroupName);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ssn_access_log WHERE user_group_id = ?",
                Long.class, otherGroup)).isEqualTo(1);

        // Its admin signs in to an empty workspace and can start again.
        perform(get("/providers").with(signedIn(otherAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Shah"))));
        fill(otherAdmin);
        assertThat(rows(otherGroup)).isEqualTo(otherBefore);

        perform(get("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)));
        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));
        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                .andExpect(flash().attribute("message", otherGroupName + " had no data to wipe."));
    }

    @Test
    void theWrongNameOrPasswordWipesNothing() throws Exception {
        Map<String, Long> before = rows(otherGroup);

        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName.toUpperCase()).param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Type the group&#39;s name exactly as shown")));
        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName).param("currentPassword", "not-my-password-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That isn&#39;t your current password")));
        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName))
                .andExpect(status().isOk());
        // Another group's name doesn't count either.
        perform(post("/admin/user-groups/" + otherGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", jdbc.queryForObject("SELECT name FROM user_groups WHERE id = ?",
                                String.class, superGroup))
                        .param("currentPassword", PASSWORD))
                .andExpect(status().isOk());

        assertThat(rows(otherGroup)).isEqualTo(before);
    }

    @Test
    void aSuperuserCanWipeTheirOwnGroupEvenWhileViewingAnother() throws Exception {
        Map<String, Long> otherBefore = rows(otherGroup);
        String ownName = jdbc.queryForObject("SELECT name FROM user_groups WHERE id = ?", String.class, superGroup);

        perform(post("/admin/user-groups/" + otherGroup + "/view").with(signedIn(superuser)).with(csrf())
                        .with(inSession()))
                .andExpect(redirectedUrl("/providers"));
        perform(get("/admin/user-groups/" + superGroup + "/wipe").with(signedIn(superuser)).with(inSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("your own group")));
        perform(post("/admin/user-groups/" + superGroup + "/wipe").with(signedIn(superuser)).with(csrf())
                        .with(inSession()).param("confirmName", ownName).param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(rows(superGroup)).allSatisfy((table, count) -> assertThat(count).as(table).isZero());
        assertThat(rows(otherGroup)).isEqualTo(otherBefore);
        assertThat(users.findById(superuser.getId()).getRole()).isEqualTo(Role.SUPERUSER);
    }

    @Test
    void anAdminCantWipeAnyGroupNotEvenTheirOwn() throws Exception {
        Map<String, Long> before = rows(otherGroup);
        for (Long group : new Long[]{otherGroup, superGroup}) {
            perform(get("/admin/user-groups/" + group + "/wipe").with(signedIn(otherAdmin)))
                    .andExpect(status().isForbidden());
            perform(post("/admin/user-groups/" + group + "/wipe").with(signedIn(otherAdmin)).with(csrf())
                            .param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                    .andExpect(status().isForbidden());
        }
        perform(get("/admin/users").with(signedIn(otherAdmin)))
                .andExpect(content().string(not(containsString("/wipe"))));
        assertThat(rows(otherGroup)).isEqualTo(before);
    }

    /** Rows per wiped table for one group. */
    private Map<String, Long> rows(Long group) {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String table : UserGroupWipeService.WIPED.keySet()) {
            rows.put(table, jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE user_group_id = ?", Long.class, group));
        }
        return rows;
    }

    private void fill(User account) {
        ImportReport report = as(account, () -> imports.importFile(TestWorkbook.fullPractice().bytes()));
        assertThat(report.problems()).isEmpty();
        // Imports don't bring documents, so add one directly; the wipe has to reach every table.
        jdbc.update("""
                INSERT INTO documents (user_group_id, provider_id, doc_type, file_name, content_type, size_bytes,
                                       content, uploaded_by)
                SELECT user_group_id, min(id), 'cv', 'cv.pdf', 'application/pdf', 3, '\\x010203'::bytea, 'test'
                FROM providers WHERE user_group_id = ? GROUP BY user_group_id""", account.getUserGroupId());
        // Nor training or work history.
        jdbc.update("""
                INSERT INTO provider_training (user_group_id, provider_id, training_type, institution, start_date)
                SELECT user_group_id, min(id), 'RESIDENCY', 'General Hospital', DATE '2012-07-01'
                FROM providers WHERE user_group_id = ? GROUP BY user_group_id""", account.getUserGroupId());
        jdbc.update("""
                INSERT INTO provider_work_history (user_group_id, provider_id, entry_type, employer, start_date)
                SELECT user_group_id, min(id), 'JOB', 'Lakeside Clinic', DATE '2015-08-01'
                FROM providers WHERE user_group_id = ? GROUP BY user_group_id""", account.getUserGroupId());
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

    private RequestPostProcessor inSession() {
        return request -> {
            if (sessionCookie != null) {
                request.setCookies(sessionCookie);
            }
            return request;
        };
    }

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

    private <T> T as(User actor, java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new CredAppUserDetails(actor);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
