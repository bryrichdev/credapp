package dev.bryrich.credapp.security;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.*;
import dev.bryrich.credapp.license.*;
import dev.bryrich.credapp.owner.*;
import dev.bryrich.credapp.payer.*;
import dev.bryrich.credapp.provider.*;
import dev.bryrich.credapp.registration.*;
import dev.bryrich.credapp.ssn.*;
import dev.bryrich.credapp.user.*;
import dev.bryrich.credapp.usergroup.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountAccessIntegrationTest {
    private static final String PASSWORD = "test-password-1234";
    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired UserGroupRepository userGroups;
    @Autowired ProviderRepository providers;
    @Autowired OwnerRepository owners;
    @Autowired GroupRepository groups;
    @Autowired PayerRepository payers;
    @Autowired LicenseRepository licenses;
    @Autowired SsnAccessLogRepository audit;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void schemaRequiresGroupMembershipAndUngroupedWorkCannotReadExistingRecords() {
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.columns
                where table_schema = 'public' and column_name = 'user_group_id'
                  and (column_default is not null or is_nullable <> 'NO')
                """, String.class)).isEmpty();
        assertThat(userGroups.findAll()).noneMatch(group -> group.getName().equals("Default user group"));
        assertThatThrownBy(() -> jdbc.update("""
                insert into users (email, password_hash) values (?, 'unused-test-hash')
                """, email())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into users (email, password_hash, user_group_id) values (?, 'unused-test-hash', 0)
                """, email())).isInstanceOf(DataIntegrityViolationException.class);
        User admin = admin();
        Records data = records(admin, "ExplicitGroupOnly");
        SecurityContextHolder.clearContext();
        assertThat(providers.findAll()).isEmpty();
        assertThat(providers.findById(data.providerId())).isEmpty();
        assertThatThrownBy(() -> providers.saveAndFlush(new Provider("No", "Group")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registrationCreatesAnIsolatedAdminAndCoordinatorWaitsForTheirAdmin() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(containsString("/register")));
        mvc.perform(get("/register")).andExpect(status().isOk());
        String adminEmail = email();
        mvc.perform(signup(adminEmail, "ADMIN").param("groupName", "New workspace").with(csrf()))
                .andExpect(redirectedUrl("/login"));
        User admin = users.findByEmail(adminEmail).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getUserGroupId()).isPositive();
        assertThat(passwords.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        UserGroup workspace = userGroups.findById(admin.getUserGroupId()).orElseThrow();

        String coordinatorEmail = email();
        mvc.perform(signup(coordinatorEmail, "COORDINATOR").param("joinCode", workspace.getJoinCode()).with(csrf()))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", containsString("approve")));
        User coordinator = users.findByEmail(coordinatorEmail).orElseThrow();
        assertThat(coordinator.isPendingApproval()).isTrue();
        assertThat(coordinator.isEnabled()).isFalse();
        assertThat(coordinator.getUserGroupId()).isEqualTo(admin.getUserGroupId());
        mvc.perform(post("/login").with(csrf()).param("username", coordinatorEmail).param("password", PASSWORD))
                .andExpect(unauthenticated());
        mvc.perform(get("/api/providers").param("lastName", "").with(httpBasic(coordinatorEmail, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users").with(user(new CredAppUserDetails(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pending approval")))
                .andExpect(content().string(containsString(workspace.getJoinCode())));

        User outsider = admin();
        mvc.perform(post("/admin/users/" + coordinator.getId() + "/approval")
                        .with(user(new CredAppUserDetails(outsider))).with(csrf()).param("approve", "true"))
                .andExpect(flash().attribute("errorMessage", containsString("own user group")));
        assertThat(users.findById(coordinator.getId()).isEnabled()).isFalse();
        mvc.perform(post("/admin/users/" + coordinator.getId() + "/approval")
                        .with(user(new CredAppUserDetails(admin))).with(csrf()).param("approve", "true"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(post("/login").with(csrf()).param("username", coordinatorEmail).param("password", PASSWORD))
                .andExpect(authenticated());
    }

    @Test
    void invalidRegistrationCannotMintPrivilegedUsersOrOrphanGroups() throws Exception {
        long groupCount = userGroups.count();
        // Without a CSRF token nothing is created; the form comes back as expired.
        mvc.perform(signup(email(), "ADMIN").param("groupName", "No CSRF"))
                .andExpect(redirectedUrl("/register?expired"));
        for (String role : List.of("SUPERUSER", "READONLY", "not-a-role")) {
            mvc.perform(signup(email(), role).with(csrf())).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "role"));
        }
        mvc.perform(signup(email(), "ADMIN").param("groupName", "Mismatch").with(csrf())
                        .with(request -> { request.setParameter("confirmPassword", "wrong-confirmation"); return request; }))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
        mvc.perform(signup(email(), "COORDINATOR").param("joinCode", UUID.randomUUID().toString()).with(csrf()))
                .andExpect(content().string(containsString("group code was not found")));
        assertThat(userGroups.count()).isEqualTo(groupCount);
        User admin = admin();
        long withAdmin = userGroups.count();
        mvc.perform(signup(admin.getEmail().toUpperCase(Locale.ROOT), "ADMIN")
                        .param("groupName", "Duplicate").with(csrf()))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
        assertThat(userGroups.count()).isEqualTo(withAdmin);
    }

    @Test
    void everyRecordListAndDirectLookupStaysWithinItsUserGroup() throws Exception {
        User first = admin();
        User second = admin();
        Records a = records(first, "FirstWorkspace");
        Records b = records(second, "OtherWorkspace");
        Map<String, Long> own = a.paths();
        for (var entry : b.paths().entrySet()) {
            String path = entry.getKey();
            mvc.perform(get(path).with(user(new CredAppUserDetails(first))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("FirstWorkspace")))
                    .andExpect(content().string(not(containsString("OtherWorkspace"))));
            // Licenses have a list and edit page; their read detail is under the provider API.
            if (!path.equals("/licenses")) {
                mvc.perform(get(path + "/" + own.get(path)).with(user(new CredAppUserDetails(first))))
                        .andExpect(status().isOk());
                mvc.perform(get(path + "/" + entry.getValue()).with(user(new CredAppUserDetails(first))))
                        .andExpect(status().isNotFound());
            }
        }
        mvc.perform(get("/api/providers/" + b.providerId()).with(httpBasic(first.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/providers/" + b.providerId()).with(httpBasic(first.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/providers/" + b.providerId() + "/ssn").with(csrf()).with(user(new CredAppUserDetails(first))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/providers/" + b.providerId() + "/licenses").with(httpBasic(first.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/licenses").param("name", "OtherWorkspace").with(httpBasic(first.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("OtherWorkspace"))));
        as(first, () -> {
            assertThat(providers.findById(b.providerId())).isEmpty();
            assertThat(licenses.findById(b.licenseId())).isEmpty();
            assertThat(providers.findAll()).hasSize(1);
            assertThat(audit.findAll()).isEmpty();
            return null;
        });
        assertThatThrownBy(() -> jdbc.update("""
                insert into payer_contacts (user_group_id, payer_id, provider_id, role)
                values (?, ?, ?, 'Billing')
                """, first.getUserGroupId(), a.payerId(), b.providerId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readOnlyCanBrowseAndRevealSsnButCannotChangeRecordsOrOpenEditingPages() throws Exception {
        User admin = admin();
        Records data = records(admin, "VisibleWorkspace");
        User reader = users.createAs(admin, email(), PASSWORD, "Reader", Role.READONLY);
        for (String path : List.of("/providers", "/groups", "/owners", "/payers", "/licenses")) {
            mvc.perform(get(path).with(user(new CredAppUserDetails(reader))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("VisibleWorkspace")))
                    .andExpect(content().string(not(containsString(">Delete</button>"))))
                    .andExpect(content().string(not(containsString(path + "/new"))));
            mvc.perform(get(path + "/new").with(user(new CredAppUserDetails(reader))))
                    .andExpect(status().isForbidden());
        }
        for (var entry : data.paths().entrySet()) {
            String recordPath = entry.getKey() + "/" + entry.getValue();
            if (!entry.getKey().equals("/licenses")) {
                mvc.perform(get(recordPath).with(user(new CredAppUserDetails(reader))))
                        .andExpect(status().isOk())
                        .andExpect(content().string(not(containsString("danger-zone"))))
                        .andExpect(content().string(not(containsString(recordPath + "/edit"))));
            }
            for (String suffix : List.of("/edit", "/delete")) {
                mvc.perform(post(recordPath + suffix).with(csrf()).with(user(new CredAppUserDetails(reader))))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(post(entry.getKey()).with(csrf()).with(user(new CredAppUserDetails(reader))))
                    .andExpect(status().isForbidden());
            mvc.perform(get(recordPath + "/edit").with(user(new CredAppUserDetails(reader))))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of("/owners/" + data.ownerId() + "?edit=1",
                "/payers/" + data.payerId() + "?edit=true", "/owners/1/groups/new", "/payers/1/contacts/new",
                "/payers/1/contacts/1/edit", "/admin/users", "/admin/users/new")) {
            mvc.perform(get(path).with(user(new CredAppUserDetails(reader)))).andExpect(status().isForbidden());
        }
        for (String path : List.of("/owners/1/groups", "/owners/1/groups/1/delete", "/payers/1/contacts",
                "/payers/1/contacts/1/edit", "/payers/1/contacts/1/delete", "/admin/users")) {
            mvc.perform(post(path).with(csrf()).with(user(new CredAppUserDetails(reader)))).andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/providers", "/api/providers/" + data.providerId() + "/licenses", "/api/users")) {
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                                .with(httpBasic(reader.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/api/providers/" + data.providerId()).with(httpBasic(reader.getEmail(), PASSWORD)))
                .andExpect(status().isOk());
        for (String path : List.of("/owners/" + data.ownerId(), "/providers/" + data.providerId())) {
            mvc.perform(post(path + "/ssn").with(csrf()).with(user(new CredAppUserDetails(reader))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.ssn").value("123456789"));
        }
        as(reader, () -> {
            assertThat(audit.findAll()).hasSize(2).allSatisfy(log -> {
                assertThat(log.getUserId()).isEqualTo(reader.getId());
                assertThat(log.getUserGroupId()).isEqualTo(reader.getUserGroupId());
            });
            assertThat(providers.findById(data.providerId())).isPresent();
            return null;
        });
        mvc.perform(post("/logout").with(csrf()).with(user(new CredAppUserDetails(reader))))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void administratorsCannotReadOrPromoteAccountsInAnotherWorkspaceOrMintSuperusers() throws Exception {
        User first = admin();
        User second = admin();
        User foreign = users.createAs(second, email(), PASSWORD, "Private account", Role.COORDINATOR);
        mvc.perform(get("/admin/users").with(user(new CredAppUserDetails(first))))
                .andExpect(content().string(not(containsString(foreign.getEmail()))));
        mvc.perform(get("/admin/users/" + foreign.getId() + "/edit").with(user(new CredAppUserDetails(first))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + foreign.getId()).with(httpBasic(first.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").with(httpBasic(first.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"%s", "password":"%s", "role":"superuser"}
                                """.formatted(email(), PASSWORD)))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> users.changeRoleAs(first, foreign.getId(), Role.ADMIN))
                .isInstanceOf(UserManagementDeniedException.class);
    }

    @Test
    void demotionAndDisablementApplyToExistingSessionsAndRejectedRequestsStayBlocked() throws Exception {
        User admin = admin();
        User coordinator = users.createAs(admin, email(), PASSWORD, "Coordinator", Role.COORDINATOR);
        Cookie session = mvc.perform(post("/login").with(csrf())
                        .param("username", coordinator.getEmail()).param("password", PASSWORD))
                .andExpect(authenticated()).andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        mvc.perform(get("/providers/new").cookie(session)).andExpect(status().isOk());
        users.changeRoleAs(admin, coordinator.getId(), Role.READONLY);
        mvc.perform(get("/providers/new").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(post("/providers").cookie(session).with(csrf())).andExpect(status().isForbidden());
        users.setEnabledAs(admin, coordinator.getId(), false);
        mvc.perform(get("/providers").cookie(session)).andExpect(status().isForbidden());

        RegistrationForm request = form(email(), Role.COORDINATOR);
        request.setJoinCode(userGroups.findById(admin.getUserGroupId()).orElseThrow().getJoinCode());
        User pending = registration.register(request);
        assertThatThrownBy(() -> users.setEnabledAs(admin, pending.getId(), true))
                .isInstanceOf(UserManagementDeniedException.class);
        users.decideJoinRequestAs(admin, pending.getId(), false);
        assertThat(users.findById(pending.getId()).getMembershipStatus()).isEqualTo(MembershipStatus.REJECTED);
        mvc.perform(post("/login").with(csrf()).param("username", pending.getEmail()).param("password", PASSWORD))
                .andExpect(unauthenticated());
    }

    private User admin() { return registration.register(form(email(), Role.ADMIN)); }
    private static String email() { return UUID.randomUUID() + "@example.com"; }
    private static RegistrationForm form(String email, Role role) {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(email); form.setFullName("Test User"); form.setRole(role);
        form.setPassword(PASSWORD); form.setConfirmPassword(PASSWORD); form.setGroupName("Test workspace");
        return form;
    }
    private static MockHttpServletRequestBuilder signup(String email, String role) {
        return post("/register").param("email", email).param("fullName", "New User")
                .param("password", PASSWORD).param("confirmPassword", PASSWORD).param("role", role);
    }
    private <T> T as(User actor, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new CredAppUserDetails(actor);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private Records records(User actor, String label) {
        return as(actor, () -> {
            Provider provider = new Provider(label, label);
            provider.setNpi("1234567890"); provider.setSsn("123456789");
            provider = providers.saveAndFlush(provider);
            Group group = groups.saveAndFlush(new Group(label, "123456789"));
            Owner owner = new Owner(label, label); owner.setSsn("123456789");
            owner = owners.saveAndFlush(owner);
            Payer payer = payers.saveAndFlush(new Payer(label));
            License license = new License("MI", "A123", "MD", LocalDate.now().plusYears(1), LicenseStatus.ACTIVE);
            license.setProvider(provider); license = licenses.saveAndFlush(license);
            return new Records(provider.getId(), group.getId(), owner.getId(), payer.getId(), license.getId());
        });
    }
    private record Records(Long providerId, Long groupId, Long ownerId, Long payerId, Long licenseId) {
        Map<String, Long> paths() {
            return Map.of("/providers", providerId, "/groups", groupId, "/owners", ownerId,
                    "/payers", payerId, "/licenses", licenseId);
        }
    }
}
