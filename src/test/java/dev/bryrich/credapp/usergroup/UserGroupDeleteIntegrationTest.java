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

/** Deleting a whole user group, end to end against Postgres. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserGroupDeleteIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;

    private User superuser;
    private Long superGroup;
    private User otherAdmin;
    private User otherCoordinator;
    private Long otherGroup;
    private String otherGroupName;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        superuser = users.promoteToSuperuser(register("Super workspace " + UUID.randomUUID()).getId());
        superGroup = superuser.getUserGroupId();
        otherGroupName = "Doomed practice " + UUID.randomUUID();
        otherAdmin = register(otherGroupName);
        otherGroup = otherAdmin.getUserGroupId();
        otherCoordinator = users.createInGroup(UUID.randomUUID() + "@example.com", PASSWORD, "Casey Coordinator",
                Role.COORDINATOR, otherGroup);
        fill(superuser);
        fill(otherAdmin);
        for (Long group : new Long[]{otherGroup, superGroup}) {
            jdbc.update("""
                    INSERT INTO ssn_access_log (subject_type, subject_id, subject_name, user_id, user_email, user_group_id)
                    VALUES ('owner', 1, 'Lee, Ann', 1, 'someone@example.com', ?)""", group);
            jdbc.update("""
                    INSERT INTO caqh_password_access_log (user_group_id, provider_id, provider_name, user_id, user_email)
                    VALUES (?, 1, 'Shah, Priya', 1, 'someone@example.com')""", group);
            jdbc.update("INSERT INTO tracking_settings (user_group_id) VALUES (?) ON CONFLICT DO NOTHING", group);
            jdbc.update("""
                    INSERT INTO document_access_log (user_group_id, document_id, file_name, user_id, user_email)
                    VALUES (?, 1, 'cv.pdf', 1, 'someone@example.com')""", group);
        }
        jdbc.update("INSERT INTO password_reset_requests (user_group_id, user_id, status) VALUES (?, ?, 'PENDING')",
                otherGroup, otherCoordinator.getId());
        jdbc.update("INSERT INTO password_reset_requests (user_group_id, user_id, status) VALUES (?, ?, 'PENDING')",
                superGroup, superuser.getId());
        signedInSession(otherAdmin.getEmail().toUpperCase());
        signedInSession(superuser.getEmail());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDeleteRemovesEveryGroupScopedTableTheWipeKnowsAbout() {
        Set<String> scoped = new HashSet<>(jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE column_name = 'user_group_id' AND table_schema = current_schema()""", String.class));
        Set<String> deleted = new HashSet<>(UserGroupWipeService.DELETE_ORDER);
        deleted.addAll(UserGroupDeleteService.AFTER_WIPE);
        assertThat(deleted).containsExactlyInAnyOrderElementsOf(scoped);
    }

    @Test
    void deletingAGroupRemovesItsDataAccountsAndSessionsAndNothingElse() throws Exception {
        Map<String, Long> otherBefore = rows(otherGroup);
        Map<String, Long> superBefore = rows(superGroup);
        assertThat(otherBefore).as("the fixture should reach every table, so the delete order is proven")
                .allSatisfy((table, count) -> assertThat(count).as(table).isPositive());
        long records = UserGroupWipeService.WIPED.keySet().stream().mapToLong(otherBefore::get).sum();

        perform(get("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(otherGroupName)))
                .andExpect(content().string(containsString("Malpractice claims")))
                .andExpect(content().string(containsString("2 accounts")))
                .andExpect(content().string(containsString(otherCoordinator.getEmail() + " · Coordinator")))
                .andExpect(content().string(containsString("/admin/user-groups/" + otherGroup + "/wipe")));

        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", " " + otherGroupName + " ").param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("message",
                        "Deleted " + otherGroupName + ": " + records + " records and 2 accounts."));

        assertThat(rows(otherGroup)).allSatisfy((table, count) -> assertThat(count).as(table).isZero());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_groups WHERE id = ?", Long.class, otherGroup))
                .isZero();
        assertThat(sessions(otherAdmin.getEmail())).isZero();
        assertThat(rows(superGroup)).isEqualTo(superBefore);
        assertThat(sessions(superuser.getEmail())).isEqualTo(1);

        // Anyone from the group still signed in is turned away on their next request.
        perform(get("/providers").with(signedIn(otherAdmin))).andExpect(status().isForbidden());
        perform(get("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)))
                .andExpect(status().isNotFound());
        perform(get("/admin/users").with(signedIn(superuser)))
                .andExpect(content().string(not(containsString(otherGroupName))));
    }

    @Test
    void aSuperuserCantDeleteTheirOwnGroup() throws Exception {
        String ownName = jdbc.queryForObject("SELECT name FROM user_groups WHERE id = ?", String.class, superGroup);
        Map<String, Long> before = rows(superGroup);

        perform(get("/admin/user-groups/" + superGroup + "/delete").with(signedIn(superuser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You can&#39;t delete your own user group")))
                .andExpect(content().string(not(containsString("currentPassword"))));
        perform(post("/admin/user-groups/" + superGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", ownName).param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This group can't be deleted")));

        assertThat(rows(superGroup)).isEqualTo(before);
        perform(get("/admin/users").with(signedIn(superuser)))
                .andExpect(content().string(containsString("/admin/user-groups/" + otherGroup + "/delete")))
                .andExpect(content().string(not(containsString("/admin/user-groups/" + superGroup + "/delete"))));
    }

    @Test
    void aGroupWithAnotherSuperuserCantBeDeletedUntilTheyreDemoted() throws Exception {
        users.promoteToSuperuser(otherCoordinator.getId());
        Map<String, Long> before = rows(otherGroup);

        perform(get("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This group has another superuser")))
                .andExpect(content().string(containsString(otherCoordinator.getEmail())));
        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This group has another superuser")));
        assertThat(rows(otherGroup)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_groups WHERE id = ?", Long.class, otherGroup))
                .isEqualTo(1);
    }

    @Test
    void theWrongNameOrPasswordDeletesNothing() throws Exception {
        Map<String, Long> before = rows(otherGroup);

        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName.toUpperCase()).param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Type the group&#39;s name exactly as shown")));
        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName).param("currentPassword", "not-my-password-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That isn&#39;t your current password")));
        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .param("confirmName", otherGroupName))
                .andExpect(status().isOk());

        assertThat(rows(otherGroup)).isEqualTo(before);
    }

    @Test
    void deletingTheGroupYoureViewingTakesYouBackToYourOwn() throws Exception {
        perform(post("/admin/user-groups/" + otherGroup + "/view").with(signedIn(superuser)).with(csrf())
                        .with(inSession()))
                .andExpect(redirectedUrl("/providers"));
        perform(get("/admin/users").with(signedIn(superuser)).with(inSession()))
                .andExpect(content().string(containsString("viewing-banner")));

        perform(post("/admin/user-groups/" + otherGroup + "/delete").with(signedIn(superuser)).with(csrf())
                        .with(inSession()).param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));

        perform(get("/admin/users").with(signedIn(superuser)).with(inSession()))
                .andExpect(content().string(not(containsString("viewing-banner"))));
        perform(get("/providers").with(signedIn(superuser)).with(inSession()))
                .andExpect(content().string(containsString("Shah")));
    }

    @Test
    void anAdminCantDeleteAnyGroup() throws Exception {
        Map<String, Long> before = rows(otherGroup);
        for (Long group : new Long[]{otherGroup, superGroup}) {
            perform(get("/admin/user-groups/" + group + "/delete").with(signedIn(otherAdmin)))
                    .andExpect(status().isForbidden());
            perform(post("/admin/user-groups/" + group + "/delete").with(signedIn(otherAdmin)).with(csrf())
                            .param("confirmName", otherGroupName).param("currentPassword", PASSWORD))
                    .andExpect(status().isForbidden());
        }
        perform(get("/admin/users").with(signedIn(otherAdmin)))
                .andExpect(content().string(not(containsString("/admin/user-groups/"))));
        assertThat(rows(otherGroup)).isEqualTo(before);
    }

    /** Rows per group-scoped table for one group, the wipe's tables and the ones it keeps. */
    private Map<String, Long> rows(Long group) {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String table : UserGroupWipeService.DELETE_ORDER) {
            rows.put(table, count(table, group));
        }
        for (String table : UserGroupDeleteService.AFTER_WIPE) {
            rows.put(table, count(table, group));
        }
        return rows;
    }

    private long count(String table, Long group) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_group_id = ?", Long.class, group);
    }

    private long sessions(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE lower(principal_name) = lower(?)",
                Long.class, email);
    }

    private void signedInSession(String principalName) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,
                                            max_inactive_interval, expiry_time, principal_name)
                VALUES (?, ?, ?, ?, 1800, ?, ?)""",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), now, now, now + 1_800_000, principalName);
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
        long template = jdbc.queryForObject("""
                INSERT INTO application_templates (user_group_id, payer_id, name, content, mappings, created_by)
                SELECT user_group_id, min(id), 'Application', decode('010203', 'hex'), decode('010203', 'hex'), 'test'
                FROM payers WHERE user_group_id = ? GROUP BY user_group_id RETURNING id
                """, Long.class, account.getUserGroupId());
        long run = jdbc.queryForObject("""
                INSERT INTO application_runs (user_group_id, provider_id, template_id, template_revision, field_values, created_by)
                SELECT user_group_id, min(id), ?, 1, decode('010203', 'hex'), 'test'
                FROM providers WHERE user_group_id = ? GROUP BY user_group_id RETURNING id
                """, Long.class, template, account.getUserGroupId());
        jdbc.update("INSERT INTO application_access_log (user_group_id, run_id, user_id, user_email, action) VALUES (?, ?, ?, ?, 'review')",
                account.getUserGroupId(), run, account.getId(), account.getEmail());
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
