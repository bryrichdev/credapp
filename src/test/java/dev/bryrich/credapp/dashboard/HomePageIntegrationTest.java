package dev.bryrich.credapp.dashboard;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.MembershipStatus;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_PAYERS;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The home page for an empty group, a working one, and who sees what. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomePageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired OnboardingImportService imports;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = register();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anEmptyGroupIsShownHowToStart() throws Exception {
        mvc.perform(get("/").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome back, Lena")))
                .andExpect(content().string(containsString("Get started")))
                .andExpect(content().string(containsString("href=\"/admin/import\"")))
                .andExpect(content().string(containsString("href=\"/admin/tracking-settings\"")))
                .andExpect(content().string(not(containsString("Needs attention"))));

        User viewer = readOnly();
        mvc.perform(get("/").with(signedIn(viewer)))
                .andExpect(content().string(containsString("Get started")))
                .andExpect(content().string(not(containsString("href=\"/admin/import\""))))
                .andExpect(content().string(not(containsString("href=\"/providers/new\""))));
    }

    @Test
    void aWorkingGroupSeesWhatNeedsDoingWhatsOnFileAndWhereApplicationsStand() throws Exception {
        LocalDate today = LocalDate.now();
        as(admin);
        imports.importFile(new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah")
                .row(PROVIDERS, "Provider ID", "P2", "First name", "Tom", "Last name", "Ng")
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "35.1", "License type", "MD",
                        "Expiration date", today.minusDays(4).toString())
                .row(LICENSES, "Provider ID", "P2", "State", "OH", "License number", "35.2", "License type", "MD",
                        "Expiration date", today.plusDays(300).toString())
                .row(PAYERS, "Payer ID", "PAY1", "Name", "Aetna")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1", "Status", "Active",
                        "Effective date", "2024-01-01")
                .row(PROVIDER_PAYERS, "Provider ID", "P1", "Payer ID", "PAY1", "Status", "Submitted",
                        "Submitted date", today.minusDays(10).toString())
                .row(PROVIDER_PAYERS, "Provider ID", "P2", "Payer ID", "PAY1", "Status", "Submitted")
                .bytes());
        SecurityContextHolder.clearContext();

        User waiting = new User(UUID.randomUUID() + "@example.com", "unused-hash", admin.getUserGroupId());
        waiting.setRole(Role.COORDINATOR);
        waiting.setMembershipStatus(MembershipStatus.PENDING);
        waiting.setEnabled(false);
        users.saveAndFlush(waiting);

        mvc.perform(get("/").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Get started"))))
                .andExpect(content().string(containsString("1 person is waiting for you to approve their account.")))
                // What needs doing: the lapsed license, with a way to fix it.
                .andExpect(content().string(containsString("OH MD license 35.1")))
                .andExpect(content().string(containsString("4 days ago")))
                .andExpect(content().string(containsString("/edit#licenses")))
                // On file.
                .andExpect(content().string(containsString("<span>Providers</span><strong>2</strong>")))
                .andExpect(content().string(containsString("<span>Payers</span><strong>1</strong>")))
                .andExpect(content().string(containsString("<span>Licenses in use</span><strong>2</strong>")))
                // Where applications stand: one active, two submitted.
                .andExpect(content().string(containsString("title=\"Active: 1\"")))
                .andExpect(content().string(containsString("title=\"Submitted: 2\"")))
                // Recently updated.
                .andExpect(content().string(containsString("Lakeside Clinic LLC</a>")))
                .andExpect(content().string(containsString("Shah, Priya</a>")));

        User viewer = readOnly();
        mvc.perform(get("/").with(signedIn(viewer)))
                .andExpect(content().string(containsString("OH MD license 35.1")))
                .andExpect(content().string(not(containsString("/edit#licenses"))))
                .andExpect(content().string(not(containsString("waiting for you to approve"))));
    }

    private User readOnly() {
        User viewer = new User(UUID.randomUUID() + "@example.com", "unused-hash", admin.getUserGroupId());
        viewer.setRole(Role.READONLY);
        return users.saveAndFlush(viewer);
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Lena Lake");
        form.setRole(Role.ADMIN);
        form.setPassword("test-password-1234");
        form.setConfirmPassword("test-password-1234");
        form.setGroupName("Home " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }

    private static void as(User actor) {
        var principal = new CredAppUserDetails(actor);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
