package dev.bryrich.credapp.tracking;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.ImportReport;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.CERTIFICATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.HOSPITAL_PRIVILEGES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_PAYERS;
import static dev.bryrich.credapp.tracking.TrackedKind.CAQH_ATTESTATION;
import static dev.bryrich.credapp.tracking.TrackedKind.CERTIFICATION;
import static dev.bryrich.credapp.tracking.TrackedKind.FLU_SHOT;
import static dev.bryrich.credapp.tracking.TrackedKind.FOLLOW_UP;
import static dev.bryrich.credapp.tracking.TrackedKind.LICENSE;
import static dev.bryrich.credapp.tracking.TrackedKind.MALPRACTICE_POLICY;
import static dev.bryrich.credapp.tracking.TrackedKind.REAPPOINTMENT;
import static dev.bryrich.credapp.tracking.TrackedKind.RECREDENTIAL;
import static dev.bryrich.credapp.tracking.TrackedKind.STALLED;
import static dev.bryrich.credapp.tracking.TrackedKind.TB_TEST;
import static dev.bryrich.credapp.tracking.TrackedState.COMING_UP;
import static dev.bryrich.credapp.tracking.TrackedState.DUE_SOON;
import static dev.bryrich.credapp.tracking.TrackedState.OVERDUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** What the tracking report finds, against Postgres, on a fixed "today". */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class TrackingServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    @Autowired TrackingService tracking;
    @Autowired TrackingSettingsService settings;
    @Autowired OnboardingImportService imports;
    @Autowired UserGroupRepository userGroups;

    private Long group;

    @BeforeEach
    void aPracticeWithSomethingOfEverything() {
        group = signInToNewGroup();
        ImportReport report = imports.importFile(practice().bytes());
        assertThat(report.problems()).isEmpty();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void itListsEverythingDueWithTheRightStateMostPressingFirst() {
        var report = tracking.report(group, TODAY);

        assertThat(report.items())
                .extracting(TrackedItem::kind, item -> item.subject().name(), TrackedItem::state, TrackedItem::days)
                .containsExactly(
                        tuple(FLU_SHOT, "Shah, Priya", OVERDUE, -31L),
                        tuple(LICENSE, "Shah, Priya", OVERDUE, -7L),
                        tuple(RECREDENTIAL, "Shah, Priya", OVERDUE, -2L),
                        tuple(REAPPOINTMENT, "Shah, Priya", DUE_SOON, 0L),
                        tuple(FOLLOW_UP, "Lakeside Clinic LLC", DUE_SOON, 9L),
                        tuple(CAQH_ATTESTATION, "Shah, Priya", DUE_SOON, 14L),
                        tuple(LICENSE, "Ng, Tom", DUE_SOON, 19L),
                        tuple(MALPRACTICE_POLICY, "Lakeside Clinic LLC", DUE_SOON, 29L),
                        tuple(STALLED, "Lakeside Clinic LLC", TrackedState.STALLED, 92L),
                        tuple(CERTIFICATION, "Shah, Priya", COMING_UP, 44L),
                        tuple(LICENSE, "Ng, Tom", COMING_UP, 61L),
                        tuple(TB_TEST, "Shah, Priya", COMING_UP, 75L),
                        tuple(RECREDENTIAL, "Lakeside Clinic LLC", COMING_UP, 90L));

        List<TrackedItem> items = report.items();
        assertThat(items.get(1).what()).isEqualTo("OH DEA license FA1234563");
        assertThat(items.get(1).stateLabel()).isEqualTo("Expired");
        assertThat(items.get(1).when()).isEqualTo("7 days ago");
        assertThat(items.get(0).stateLabel()).isEqualTo("Overdue");
        assertThat(items.get(3).when()).isEqualTo("today");
        assertThat(items.get(3).editPath()).endsWith("/edit#privileges");
        assertThat(items.get(5).editPath()).endsWith("/edit#caqh");
        assertThat(items.get(0).editPath()).endsWith("/edit#caqh");
        assertThat(items.get(4).what()).isEqualTo("Humana");
        assertThat(items.get(8).what()).isEqualTo("Cigna, submitted");
        assertThat(items.get(8).when()).isEqualTo("waiting 92 days");
        assertThat(items.get(8).subject().path()).startsWith("/groups/");

        assertThat(report.needingAction()).isEqualTo(9);
        assertThat(report.countsByState()).containsEntry(OVERDUE, 3L).containsEntry(DUE_SOON, 5L)
                .containsEntry(TrackedState.STALLED, 1L).containsEntry(COMING_UP, 4L);
    }

    @Test
    void aGroupsOwnWindowsChangeWhatShowsAndHowUrgentItIs() {
        TrackingSettingsForm form = new TrackingSettingsForm();
        form.setWarningDays(30);
        form.setUrgentDays(7);
        form.setStalledDays(120);
        form.setCaqhDays(90);
        settings.save(group, form);

        assertThat(tracking.report(group, TODAY).items())
                .extracting(TrackedItem::kind, TrackedItem::state, TrackedItem::days)
                .containsExactly(
                        tuple(FLU_SHOT, OVERDUE, -31L),
                        tuple(CAQH_ATTESTATION, OVERDUE, -16L),
                        tuple(LICENSE, OVERDUE, -7L),
                        tuple(RECREDENTIAL, OVERDUE, -2L),
                        tuple(REAPPOINTMENT, DUE_SOON, 0L),
                        tuple(LICENSE, COMING_UP, 19L),
                        tuple(MALPRACTICE_POLICY, COMING_UP, 29L));
    }

    @Test
    void anotherGroupsRecordsNeverShow() {
        Long other = signInToNewGroup();
        imports.importFile(new TestWorkbook()
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Other", "Last name", "Person")
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "X1", "License type", "MD",
                        "Expiration date", "2026-05-01")
                .bytes());

        assertThat(tracking.report(other, TODAY).items()).extracting(item -> item.subject().name())
                .containsExactly("Person, Other");
        assertThat(tracking.report(group, TODAY).items()).extracting(item -> item.subject().name())
                .doesNotContain("Person, Other");
    }

    @Test
    void theReportNarrowsToOneProviderOrGroup() {
        var report = tracking.report(group, TODAY);
        Long priya = report.items().getFirst().subject().id();
        assertThat(report.forProvider(priya)).hasSize(7)
                .allSatisfy(item -> assertThat(item.subject().name()).isEqualTo("Shah, Priya"));
        Long lakeside = report.items().get(4).subject().id();
        assertThat(report.forGroup(lakeside)).extracting(TrackedItem::kind)
                .containsExactly(FOLLOW_UP, MALPRACTICE_POLICY, STALLED, RECREDENTIAL);
        assertThat(tracking.providersInGroup(group, lakeside)).isEmpty();
    }

    /**
     * One of each case on 2026-06-01, with the ones that shouldn't show alongside:
     * a renewed license, certification and policy; an inactive license; one beyond the
     * 90-day window; a terminated enrollment; a follow-up still far off; an application
     * that's only been waiting a month.
     */
    private static TestWorkbook practice() {
        return new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah",
                        "Flu shot date", "2025-05-01", "TB test date", "2025-08-15", "CAQH attested date", "2026-02-15")
                .row(PROVIDERS, "Provider ID", "P2", "First name", "Tom", "Last name", "Ng")
                // Expired, but a newer license for the same state and type is on file: quiet.
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "35.1", "License type", "MD",
                        "Expiration date", "2026-05-20")
                .row(LICENSES, "Provider ID", "P1", "State", "oh", "License number", "35.9", "License type", "md",
                        "Expiration date", "2028-05-31")
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "FA1234563", "License type", "DEA",
                        "Expiration date", "2026-05-25")
                .row(LICENSES, "Provider ID", "P2", "State", "OH", "License number", "35.2", "License type", "MD",
                        "Expiration date", "2026-06-20")
                .row(LICENSES, "Provider ID", "P2", "State", "MI", "License number", "43.2", "License type", "MD",
                        "Expiration date", "2026-08-01")
                .row(LICENSES, "Provider ID", "P2", "State", "PA", "License number", "MD.2", "License type", "MD",
                        "Expiration date", "2026-05-01", "Status", "Inactive")
                .row(LICENSES, "Provider ID", "P2", "State", "NY", "License number", "NY.2", "License type", "MD",
                        "Expiration date", "2027-01-01")
                .row(CERTIFICATIONS, "Provider ID", "P1", "Board", "ABFM", "Effective date", "2016-07-15",
                        "Expiration date", "2026-07-15")
                .row(CERTIFICATIONS, "Provider ID", "P2", "Board", "ABIM", "Effective date", "2016-05-01",
                        "Expiration date", "2026-05-01")
                .row(CERTIFICATIONS, "Provider ID", "P2", "Board", "abim", "Effective date", "2026-04-01")
                .row(POLICIES, "Group ID", "G1", "Policy number", "G-1", "Carrier", "MedPro",
                        "Type of coverage", "Claims-made", "Effective date", "2025-06-30",
                        "Expiration date", "2026-06-30", "Shared or individual", "Shared")
                .row(POLICIES, "Provider ID", "P2", "Policy number", "P-1", "Carrier", "MedPro",
                        "Type of coverage", "Claims-made", "Effective date", "2025-05-15",
                        "Expiration date", "2026-05-15", "Shared or individual", "Individual")
                .row(POLICIES, "Provider ID", "P2", "Policy number", "P-2", "Carrier", "Other Mutual",
                        "Type of coverage", "Claims-made", "Effective date", "2026-05-15",
                        "Expiration date", "2027-05-15", "Shared or individual", "Individual")
                .row(HOSPITAL_PRIVILEGES, "Provider ID", "P1", "Hospital", "St. Vincent",
                        "Reappointment date", "2026-06-01")
                .row(PAYERS, "Payer ID", "PAY1", "Name", "Aetna")
                .row(PAYERS, "Payer ID", "PAY2", "Name", "Cigna")
                .row(PAYERS, "Payer ID", "PAY3", "Name", "Humana")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1", "Status", "Active",
                        "Effective date", "2020-01-01", "Recredential by", "2026-08-30")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY2", "Status", "Submitted",
                        "Submitted date", "2026-03-01")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY3", "Status", "Submitted",
                        "Submitted date", "2026-03-01", "Follow up on", "2026-06-10")
                .row(PROVIDER_PAYERS, "Provider ID", "P1", "Payer ID", "PAY1", "Status", "Terminated",
                        "Recredential by", "2026-06-05")
                .row(PROVIDER_PAYERS, "Provider ID", "P1", "Payer ID", "PAY2", "Status", "Active",
                        "Effective date", "2023-06-01", "Recredential by", "2026-05-30")
                .row(PROVIDER_PAYERS, "Provider ID", "P2", "Payer ID", "PAY1", "Status", "In progress",
                        "Submitted date", "2026-05-01")
                .row(PROVIDER_PAYERS, "Provider ID", "P2", "Payer ID", "PAY2", "Follow up on", "2026-07-20");
    }

    private Long signInToNewGroup() {
        UserGroup userGroup = userGroups.saveAndFlush(new UserGroup("Tracking " + UUID.randomUUID()));
        CredAppUserDetails principal = new CredAppUserDetails(
                new User(UUID.randomUUID() + "@example.com", "unused-password-hash", userGroup.getId()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        return userGroup.getId();
    }
}
