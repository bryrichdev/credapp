package dev.bryrich.credapp.tracking;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.onboarding.xlsx.XlsxReader;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_MEMBERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The tracking pages end to end: report, filters, export, settings and who may change them. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrackingWebIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired OnboardingImportService imports;
    @Autowired TrackingSettingsService settings;
    @Autowired JdbcTemplate jdbc;

    private User admin;
    private User readOnly;

    @BeforeEach
    void setUp() {
        admin = register();
        User viewer = new User(UUID.randomUUID() + "@example.com", "unused-hash", admin.getUserGroupId());
        viewer.setRole(Role.READONLY);
        readOnly = users.saveAndFlush(viewer);

        LocalDate today = LocalDate.now();
        as(admin);
        imports.importFile(new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(GROUPS, "Group ID", "G2", "Legal business name", "North Surgery PC", "Tax ID", "987654321")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah")
                .row(PROVIDERS, "Provider ID", "P2", "First name", "Tom", "Last name", "Ng")
                .row(GROUP_MEMBERS, "Provider ID", "P1", "Group ID", "G1")
                .row(GROUP_MEMBERS, "Provider ID", "P2", "Group ID", "G2")
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "35.1", "License type", "MD",
                        "Expiration date", today.minusDays(3).toString())
                .row(LICENSES, "Provider ID", "P2", "State", "OH", "License number", "35.2", "License type", "MD",
                        "Expiration date", today.plusDays(45).toString())
                .row(PAYERS, "Payer ID", "PAY1", "Name", "Aetna")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1", "Status", "Submitted",
                        "Submitted date", today.minusDays(100).toString())
                .bytes());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theReportListsWhatsDueAndTheNavCountsWhatNeedsDoing() throws Exception {
        mvc.perform(get("/tracking").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OH MD license 35.1")))
                .andExpect(content().string(containsString("3 days ago")))
                .andExpect(content().string(containsString("Aetna, submitted")))
                .andExpect(content().string(containsString("waiting 100 days")))
                .andExpect(content().string(containsString("in 45 days")))
                // Expired license and stalled application need doing; the one in 45 days doesn't.
                .andExpect(content().string(containsString("class=\"topnav__count\" title=\"2 need attention\">2<")))
                .andExpect(content().string(containsString("/admin/tracking-settings")));

        mvc.perform(get("/tracking").param("state", "OVERDUE").with(signedIn(admin)))
                .andExpect(content().string(containsString("OH MD license 35.1")))
                .andExpect(content().string(not(containsString("Aetna, submitted"))));
        mvc.perform(get("/tracking").param("category", "PAYERS").with(signedIn(admin)))
                .andExpect(content().string(containsString("Aetna, submitted")))
                .andExpect(content().string(not(containsString("OH MD license"))));

        Long north = jdbcGroupId("North Surgery PC");
        mvc.perform(get("/tracking").param("group", north.toString()).with(signedIn(admin)))
                .andExpect(content().string(containsString("OH MD license 35.2")))
                .andExpect(content().string(not(containsString("OH MD license 35.1"))))
                .andExpect(content().string(not(containsString("Aetna"))));
    }

    @Test
    void theExportIsAWorkbookOfTheSameRows() throws Exception {
        byte[] file = mvc.perform(get("/tracking/export").param("category", "LICENSES").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("credapp-tracking-")))
                .andReturn().getResponse().getContentAsByteArray();

        XlsxWorkbook workbook = XlsxReader.read(new ByteArrayInputStream(file));
        var sheet = workbook.sheets().getFirst();
        assertThat(sheet.rows()).hasSize(3);
        var row = sheet.rows().get(1);
        assertThat(java.util.stream.IntStream.range(0, 6).mapToObj(i -> row.cell(i).text()).toList())
                .containsExactly("Expired", LocalDate.now().minusDays(3).toString(), "3 days ago", "License",
                        "OH MD license 35.1", "Shah, Priya");
    }

    @Test
    void theProviderAndGroupPagesSayWhatNeedsAttention() throws Exception {
        Long priya = jdbc.queryForObject("SELECT id FROM providers WHERE user_group_id = ? AND last_name = 'Shah'",
                Long.class, admin.getUserGroupId());

        mvc.perform(get("/providers/" + priya).with(signedIn(admin)))
                .andExpect(content().string(containsString("Needs attention")))
                .andExpect(content().string(containsString("OH MD license 35.1")))
                .andExpect(content().string(containsString("/providers/" + priya + "/edit#licenses")));
        mvc.perform(get("/groups/" + jdbcGroupId("Lakeside Clinic LLC")).with(signedIn(admin)))
                .andExpect(content().string(containsString("Needs attention")))
                .andExpect(content().string(containsString("Stalled application")));
        mvc.perform(get("/groups/" + jdbcGroupId("North Surgery PC")).with(signedIn(admin)))
                .andExpect(content().string(not(containsString("Needs attention"))));
    }

    @Test
    void onlyAnAdminChangesTheWindowsAndTheyMustMakeSense() throws Exception {
        mvc.perform(get("/tracking").with(signedIn(readOnly)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/tracking-settings"))))
                .andExpect(content().string(not(containsString("/edit#licenses"))));
        mvc.perform(get("/admin/tracking-settings").with(signedIn(readOnly)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/tracking-settings").with(signedIn(readOnly)).with(csrf())
                        .param("warningDays", "30").param("urgentDays", "7")
                        .param("stalledDays", "30").param("caqhDays", "120"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/tracking-settings").with(signedIn(admin)).with(csrf())
                        .param("warningDays", "30").param("urgentDays", "60")
                        .param("stalledDays", "3").param("caqhDays", "120"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Due soon has to fit within coming up")))
                .andExpect(content().string(containsString("At least 7 days")));
        assertThat(settings.forGroup(admin.getUserGroupId()).getWarningDays()).isEqualTo(90);

        mvc.perform(post("/admin/tracking-settings").with(signedIn(admin)).with(csrf())
                        .param("warningDays", "30").param("urgentDays", "7")
                        .param("stalledDays", "120").param("caqhDays", "90"))
                .andExpect(redirectedUrl("/tracking"));
        assertThat(settings.forGroup(admin.getUserGroupId()))
                .extracting(TrackingSettings::getWarningDays, TrackingSettings::getUrgentDays,
                        TrackingSettings::getStalledDays, TrackingSettings::getCaqhDays)
                .containsExactly(30, 7, 120, 90);

        // Waiting 100 days is no longer stalled at 120, and 45 days out is past the 30-day window.
        mvc.perform(get("/tracking").with(signedIn(admin)))
                .andExpect(content().string(not(containsString("Aetna, submitted"))))
                .andExpect(content().string(not(containsString("OH MD license 35.2"))))
                .andExpect(content().string(containsString("title=\"1 need attention\"")));
    }

    private Long jdbcGroupId(String lbn) {
        return jdbc.queryForObject("SELECT id FROM groups WHERE user_group_id = ? AND lbn = ?",
                Long.class, admin.getUserGroupId(), lbn);
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Tracking " + UUID.randomUUID());
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
