package dev.bryrich.credapp.caqh;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.provider.ProviderProfileService;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnConverter;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserRepository;
import dev.bryrich.credapp.usergroup.UserGroupWipeService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic credentials only. Exercise real encryption, Postgres, templates and security filters. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CaqhPasswordIntegrationTest {
    private static final String SECRET = "  synthetic-CAQH-é<&>-password  ";
    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SsnConverter encryption;
    @Autowired CaqhPasswordService passwords;
    @Autowired ProviderProfileService profiles;
    @Autowired UserGroupWipeService wipe;
    private User admin;

    @BeforeEach
    void setUp() { admin = register(); }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void storesCiphertextAndOnlyAnAuditedPostRevealsTheExactPassword() throws Exception {
        Long id = create(SECRET);
        String stored = ciphertext(id);
        assertThat(stored).isNotBlank().doesNotContain("synthetic-CAQH");
        assertThat(encryption.convertToEntityAttribute(stored)).isEqualTo(SECRET);

        for (String url : new String[]{"/providers", "/providers/" + id, "/providers/" + id + "/edit",
                "/api/providers/" + id, "/api/providers?lastName=Test"}) {
            mvc.perform(get(url).with(signedIn(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("synthetic-CAQH"))))
                    .andExpect(content().string(not(containsString(stored))));
        }
        assertThat(as(admin, () -> profiles.load(id).getCaqhPassword())).isNull();
        assertThat(auditCount(id)).isZero();

        mvc.perform(post(revealUrl(id)).with(signedIn(admin)).with(csrf())
                        .header("X-Forwarded-For", "198.51.100.99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").value(SECRET))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        var audit = jdbc.queryForMap("SELECT * FROM caqh_password_access_log WHERE provider_id = ?", id);
        assertThat(audit).containsEntry("user_id", admin.getId())
                .containsEntry("user_group_id", admin.getUserGroupId())
                .containsEntry("provider_name", "Test Provider")
                .containsEntry("user_email", admin.getEmail())
                .containsEntry("ip_address", "127.0.0.1");
        assertThat(audit.get("accessed_at")).isNotNull();
        assertThat(audit.toString()).doesNotContain(SECRET).doesNotContain(stored);
        mvc.perform(get("/providers/" + id).with(signedIn(admin)))
                .andExpect(content().string(containsString("CAQH password access")))
                .andExpect(content().string(containsString(admin.getEmail())));
    }

    @Test
    void blankPreservesReplacementUsesAFreshIvAndRemovalIsExplicit() throws Exception {
        Long id = create(SECRET);
        String first = ciphertext(id);
        mvc.perform(edit(id, admin).param("caqhPassword", "")).andExpect(status().is3xxRedirection());
        assertThat(ciphertext(id)).isEqualTo(first);
        mvc.perform(edit(id, admin).param("caqhPassword", SECRET)).andExpect(status().is3xxRedirection());
        assertThat(ciphertext(id)).isNotEqualTo(first);
        assertThat(encryption.convertToEntityAttribute(ciphertext(id))).isEqualTo(SECRET);
        mvc.perform(edit(id, admin).param("caqhPassword", "replacement-test-value"))
                .andExpect(status().is3xxRedirection());
        assertThat(encryption.convertToEntityAttribute(ciphertext(id))).isEqualTo("replacement-test-value");
        mvc.perform(edit(id, admin).param("removeCaqhPassword", "true")).andExpect(status().is3xxRedirection());
        assertThat(ciphertext(id)).isNull();
        mvc.perform(get("/providers/" + id).with(signedIn(admin)))
                .andExpect(content().string(not(containsString("id=\"revealCaqhPassword\""))));
        mvc.perform(post(revealUrl(id)).with(signedIn(admin)).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().json("{\"password\":null}"));
        assertThat(auditCount(id)).isEqualTo(1);
    }

    @Test
    void validationErrorsNeverEchoOrSaveTheSubmittedPassword() throws Exception {
        Long id = create(SECRET);
        String stored = ciphertext(id);
        String replacement = "DoNotEchoThisPassword";
        mvc.perform(post("/providers/" + id + "/edit").with(signedIn(admin)).with(csrf())
                        .param("details.lastName", "Provider").param("caqhPassword", replacement))
                .andExpect(status().isOk()).andExpect(view().name("provider/form"))
                .andExpect(content().string(not(containsString(replacement))));
        mvc.perform(edit(id, admin).param("caqhPassword", replacement).param("removeCaqhPassword", "true"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "caqhPassword"))
                .andExpect(content().string(not(containsString(replacement))));
        mvc.perform(edit(id, admin).param("caqhPassword", "x".repeat(1025)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "caqhPassword"))
                .andExpect(content().string(not(containsString("x".repeat(1025)))));
        assertThat(ciphertext(id)).isEqualTo(stored);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everySsnEligibleRoleCanRevealWithAnAuditEntry(Role role) throws Exception {
        Long id = create(SECRET);
        User viewer = member(role);
        mvc.perform(post(revealUrl(id)).with(signedIn(viewer)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").value(SECRET));
        assertThat(jdbc.queryForObject("SELECT user_id FROM caqh_password_access_log WHERE provider_id = ?",
                Long.class, id)).isEqualTo(viewer.getId());
        if (role == Role.READONLY) {
            mvc.perform(edit(id, viewer).param("caqhPassword", "forbidden"))
                    .andExpect(status().isForbidden());
            assertThatThrownBy(() -> as(viewer, () -> { passwords.save(id, "forbidden", false); return null; }))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void anonymousCsrfGetDisabledAccountsAndOtherWorkspacesCannotReveal() throws Exception {
        Long id = create(SECRET);
        mvc.perform(post(revealUrl(id)).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post(revealUrl(id)).with(signedIn(admin))).andExpect(status().isForbidden());
        mvc.perform(get(revealUrl(id)).with(signedIn(admin))).andExpect(status().isMethodNotAllowed());
        User outsider = register();
        mvc.perform(post(revealUrl(id)).with(signedIn(outsider)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(edit(id, outsider).param("caqhPassword", "forbidden")).andExpect(status().isNotFound());
        assertThat(as(outsider, () -> passwords.onFile(id))).isFalse();
        assertThat(as(outsider, () -> passwords.recentAccess(id))).isEmpty();
        User disabled = member(Role.COORDINATOR);
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        mvc.perform(post(revealUrl(id)).with(signedIn(disabled)).with(csrf())).andExpect(status().isForbidden());
        assertThat(auditCount(id)).isZero();
        assertThatThrownBy(() -> passwords.reveal(id, "127.0.0.1")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aSuperuserMayRevealOnlyAfterExplicitlyViewingTheOtherGroup() throws Exception {
        Long id = create(SECRET);
        User superuser = register();
        superuser.setRole(Role.SUPERUSER);
        users.saveAndFlush(superuser);
        mvc.perform(post(revealUrl(id)).with(signedIn(superuser)).with(csrf())).andExpect(status().isNotFound());
        var response = mvc.perform(post("/admin/user-groups/" + admin.getUserGroupId() + "/view")
                        .with(signedIn(superuser)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).isNotNull();
        mvc.perform(post(revealUrl(id)).cookie(session).with(signedIn(superuser)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").value(SECRET));
        mvc.perform(edit(id, superuser).cookie(session).param("caqhPassword", "forbidden"))
                .andExpect(status().isForbidden());
        var audit = jdbc.queryForMap("SELECT user_group_id, user_id FROM caqh_password_access_log WHERE provider_id = ?", id);
        assertThat(audit).containsEntry("user_group_id", admin.getUserGroupId())
                .containsEntry("user_id", superuser.getId());
    }

    @Test
    void corruptCiphertextDoesNotDecryptOnOrdinaryPagesAndFailedRevealRollsBackAudit() throws Exception {
        Long id = create(SECRET);
        byte[] bytes = java.util.Base64.getDecoder().decode(ciphertext(id));
        bytes[bytes.length - 1] ^= 1;
        jdbc.update("UPDATE providers SET caqh_password_ciphertext = ? WHERE id = ?",
                java.util.Base64.getEncoder().encodeToString(bytes), id);
        mvc.perform(get("/providers/" + id).with(signedIn(admin))).andExpect(status().isOk());
        mvc.perform(get("/providers/" + id + "/edit").with(signedIn(admin))).andExpect(status().isOk());
        assertThatThrownBy(() -> as(admin, () -> passwords.reveal(id, "127.0.0.1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(auditCount(id)).isZero();
    }

    @Test
    void auditHistorySurvivesProviderDeletionAndWorkspaceWipe() throws Exception {
        Long first = create(SECRET);
        Long second = create(SECRET);
        as(admin, () -> passwords.reveal(first, "127.0.0.1"));
        as(admin, () -> passwords.reveal(second, "127.0.0.1"));
        mvc.perform(post("/providers/" + first + "/delete").with(signedIn(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(auditCount(first)).isEqualTo(1);
        as(admin, () -> wipe.wipe(admin.getUserGroupId(), admin.getEmail()));
        assertThat(auditCount(first)).isEqualTo(1);
        assertThat(auditCount(second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM providers WHERE user_group_id = ?",
                Long.class, admin.getUserGroupId())).isZero();
    }

    private Long create(String password) throws Exception {
        String location = mvc.perform(post("/providers").with(signedIn(admin)).with(csrf())
                        .param("details.firstName", "Test").param("details.lastName", "Provider")
                        .param("caqhPassword", password))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private MockHttpServletRequestBuilder edit(Long id, User actor) {
        return post("/providers/" + id + "/edit").with(signedIn(actor)).with(csrf())
                .param("details.firstName", "Test").param("details.lastName", "Provider");
    }

    private String ciphertext(Long id) {
        return jdbc.queryForObject("SELECT caqh_password_ciphertext FROM providers WHERE id = ?", String.class, id);
    }

    private long auditCount(Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM caqh_password_access_log WHERE provider_id = ?", Long.class, id);
    }

    private User member(Role role) {
        User user = new User(UUID.randomUUID() + "@example.com", "unused-test-hash", admin.getUserGroupId());
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("CAQH Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword("synthetic-login-password");
        form.setConfirmPassword("synthetic-login-password");
        form.setGroupName("CAQH test " + UUID.randomUUID());
        return registration.register(form);
    }

    private static String revealUrl(Long id) { return "/providers/" + id + "/caqh-password"; }
    private static RequestPostProcessor signedIn(User account) { return user(new CredAppUserDetails(account)); }
    private static <T> T as(User account, Supplier<T> work) {
        var principal = new CredAppUserDetails(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return work.get(); } finally { SecurityContextHolder.clearContext(); }
    }
}
