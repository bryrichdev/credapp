package dev.bryrich.credapp.security;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.caqh.CaqhPasswordService;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderRepository;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browser security headers, the Cloudflare client address, and the Access-friendly routes. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.cloudflare.trust-client-ip=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityHardeningIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired ProviderRepository providers;
    @Autowired CaqhPasswordService caqhPasswords;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyPageSendsTheSecurityHeaders() throws Exception {
        User admin = register();
        for (var request : List.of(get("/login"), get("/").with(signedIn(admin)))) {
            mvc.perform(request.secure(true))
                    .andExpect(header().string("Content-Security-Policy", matchesPattern(
                            Pattern.quote(SecurityConfig.contentSecurityPolicy("NONCE"))
                                    .replace("NONCE", "\\E[A-Za-z0-9+/]{24}\\Q"))))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        }
        String policy = SecurityConfig.contentSecurityPolicy("abc");
        String scriptSrc = Stream.of(policy.split("; ")).filter(d -> d.startsWith("script-src ")).findFirst().orElseThrow();
        assertThat(scriptSrc).isEqualTo("script-src 'self' 'nonce-abc'");
        assertThat(policy).doesNotContain("'unsafe-eval'");

        // A new nonce every response, or it protects nothing.
        String first = mvc.perform(get("/login")).andReturn().getResponse().getHeader("Content-Security-Policy");
        String second = mvc.perform(get("/login")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(first).isNotEqualTo(second);
    }

    /** The policy blocks inline scripts, so a template that adds one would quietly stop working. */
    @Test
    void noTemplateUsesAnInlineScriptOrEventHandler() throws IOException {
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bth:src=|[^>]*\\bsrc=)[^>]*>");
        Pattern handler = Pattern.compile("\\son[a-z]+\\s*=\\s*\"", Pattern.CASE_INSENSITIVE);
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            List<Path> templates = files.filter(file -> file.toString().endsWith(".html")).toList();
            assertThat(templates).isNotEmpty();
            for (Path template : templates) {
                String html = Files.readString(template);
                assertThat(inlineScript.matcher(html).find()).as(template + " has an inline <script>").isFalse();
                assertThat(handler.matcher(html).find()).as(template + " has an inline on…= handler").isFalse();
            }
        }
    }

    @Test
    void theAuditLogRecordsTheAddressCloudflareSaw() throws Exception {
        User admin = register();
        Long providerId = as(admin, () -> {
            Long id = providers.saveAndFlush(new Provider("Priya", "Shah")).getId();
            caqhPasswords.save(id, "correct horse battery", false);
            return id;
        });

        mvc.perform(post("/providers/" + providerId + "/caqh-password").with(signedIn(admin)).with(csrf())
                        .header("CF-Connecting-IP", "203.0.113.7")
                        .header("X-Forwarded-For", "198.51.100.99"))
                .andExpect(status().isOk());
        // Anything that isn't an address is ignored rather than written to the log.
        mvc.perform(post("/providers/" + providerId + "/caqh-password").with(signedIn(admin)).with(csrf())
                        .header("CF-Connecting-IP", "<script>alert(1)</script>"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList(
                "SELECT ip_address FROM caqh_password_access_log WHERE provider_id = ? ORDER BY id",
                String.class, providerId))
                .containsExactly("203.0.113.7", "127.0.0.1");
    }

    @Test
    void comingBackFromAccessSignInReturnsASuperuserToTheUsersPage() throws Exception {
        User superuser = users.promoteToSuperuser(register().getId());
        for (String path : List.of("/admin/user-groups", "/admin/user-groups/42/view", "/admin/user-groups/view/exit")) {
            mvc.perform(get(path).with(signedIn(superuser)))
                    .andExpect(redirectedUrl("/admin/users"))
                    .andExpect(flash().attribute("message",
                            "Verified with Cloudflare Access. Choose the group action again to carry on."));
        }
        User admin = register();
        mvc.perform(get("/admin/user-groups/42/view").with(signedIn(admin)))
                .andExpect(status().isForbidden());
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Hardening " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }

    private <T> T as(User actor, java.util.function.Supplier<T> action) {
        var principal = new CredAppUserDetails(actor);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
