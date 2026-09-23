package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.GroupProfileService;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyRepository;
import dev.bryrich.credapp.owner.OwnerRepository;
import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.payer.PayerContact;
import dev.bryrich.credapp.payer.PayerContactService;
import dev.bryrich.credapp.payer.PayerRepository;
import dev.bryrich.credapp.payer.enrollment.EnrollmentStatus;
import dev.bryrich.credapp.payer.enrollment.PayerEnrollmentService;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderProfileForm;
import dev.bryrich.credapp.provider.ProviderProfileService;
import dev.bryrich.credapp.provider.ProviderRepository;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.CERTIFICATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.CLAIMS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.DISCLOSURES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_LOCATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_MEMBERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_SPECIALTIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.HOSPITAL_PRIVILEGES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERSHIP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNER_RELATIONSHIPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYER_CONTACTS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PRACTICE_LOCATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_PAYERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER_SPECIALTIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.REFERENCES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole import against Postgres. Not @Transactional on purpose: a preview's rollback
 * has to be a real one, which a test-wide transaction would hide. Each test gets its own
 * user group instead, so what one leaves behind can't collide with another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class OnboardingImportServiceTest {

    @Autowired OnboardingImportService imports;
    @Autowired UserGroupRepository userGroups;
    @Autowired ProviderRepository providers;
    @Autowired GroupRepository groups;
    @Autowired OwnerRepository owners;
    @Autowired MalpracticePolicyRepository policies;
    @Autowired ProviderProfileService providerProfiles;
    @Autowired GroupProfileService groupProfiles;
    @Autowired GroupLocationService groupLocations;
    @Autowired PayerRepository payers;
    @Autowired PayerContactService contacts;
    @Autowired PayerEnrollmentService enrollments;

    @BeforeEach
    void signInToAFreshGroup() {
        UserGroup group = userGroups.saveAndFlush(new UserGroup("Onboarding " + UUID.randomUUID()));
        CredAppUserDetails principal = new CredAppUserDetails(
                new User(UUID.randomUUID() + "@example.com", "unused-password-hash", group.getId()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPreviewChecksEverythingAndKeepsNothing() {
        ImportReport report = imports.preview(TestWorkbook.fullPractice().bytes());

        assertThat(report.problems()).isEmpty();
        assertThat(report.saved()).isFalse();
        assertThat(report.groups()).containsExactly("Lakeside Clinic LLC (G1)", "North Surgery PC (G2)");
        assertThat(report.providers()).containsExactly("Shah, Priya (P1)", "Ng, Tom (P2)");
        assertThat(report.counts()).extracting(ImportReport.SheetCount::sheet).hasSize(21);
        assertThat(report.payers()).containsExactly("Aetna (PAY1, new)", "Cigna (PAY2, new)");
        assertThat(providers.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(owners.count()).isZero();
    }

    @Test
    void anImportCreatesEverySectionLinkedUp() {
        ImportReport report = imports.importFile(TestWorkbook.fullPractice().bytes());

        assertThat(report.problems()).isEmpty();
        assertThat(report.saved()).isTrue();

        Group lakeside = groups.findByNpi("1234567893").orElseThrow();
        Group north = groups.findByTaxId("987654321").getFirst();
        GroupProfileForm lakesideForm = groupProfiles.load(lakeside.getId());
        assertThat(lakesideForm.getDetails().getDba()).isEqualTo("Lakeside");
        assertThat(lakesideForm.getOwners()).extracting(GroupProfileForm.OwnerRow::getPercentOwned)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("60"), new BigDecimal("40"));
        assertThat(lakesideForm.getRelations()).singleElement()
                .extracting(GroupProfileForm.RelationRow::getRelationship).hasToString("SPOUSE");
        assertThat(lakesideForm.getTaxonomies()).singleElement().satisfies(taxonomy -> {
            assertThat(taxonomy.getCode()).isEqualTo("207Q00000X");
            assertThat(taxonomy.isPrimary()).isTrue();
        });
        assertThat(lakesideForm.getPolicies()).singleElement()
                .extracting(GroupProfileForm.PolicyRow::getPolicyNumber).isEqualTo("GX-1");
        assertThat(groupLocations.findByGroupId(lakeside.getId())).singleElement()
                .satisfies(location -> assertThat(location.getLocationName()).isEqualTo("Main office"));
        assertThat(owners.count()).isEqualTo(2);
        Long ann = owners.findByLastNameIgnoreCaseAndDob("Lee", LocalDate.of(1970, 2, 3)).getFirst().getId();
        assertThat(owners.hasSsn(ann)).isTrue();

        Provider priya = providers.findByNpi("1111111111").orElseThrow();
        Provider tom = providers.findByNpi("2222222222").orElseThrow();
        ProviderProfileForm priyaForm = providerProfiles.load(priya.getId());
        assertThat(priyaForm.getDetails().getDob()).isEqualTo(LocalDate.of(1980, 5, 17));
        assertThat(priyaForm.getDetails().getZipCode()).isEqualTo("02134");
        assertThat(priyaForm.getGroups()).singleElement().satisfies(membership -> {
            assertThat(membership.getGroupId()).isEqualTo(lakeside.getId());
            assertThat(membership.getEffectiveDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        });
        assertThat(priyaForm.getLocations()).singleElement()
                .satisfies(location -> assertThat(location.getPcpScp()).hasToString("PCP"));
        assertThat(priyaForm.getTaxonomies()).singleElement()
                .satisfies(taxonomy -> assertThat(taxonomy.isPrimary()).isTrue());
        assertThat(priyaForm.getLicenses()).singleElement()
                .satisfies(license -> assertThat(license.getExpirationDate()).isEqualTo(LocalDate.of(2027, 1, 31)));
        assertThat(priyaForm.getCertifications()).hasSize(1);
        assertThat(priyaForm.getReferences()).hasSize(1);
        assertThat(priyaForm.getPolicies()).singleElement()
                .extracting(ProviderProfileForm.PolicyRow::getPolicyNumber).isEqualTo("PX-1");
        Long ownPolicy = policies.findByCarrierNameAndPolicyNumber("MedPro", "PX-1").orElseThrow().getId();
        assertThat(priyaForm.getClaims()).singleElement()
                .satisfies(claim -> assertThat(claim.getPolicyKey()).isEqualTo("p" + ownPolicy));

        ProviderProfileForm tomForm = providerProfiles.load(tom.getId());
        assertThat(tomForm.getGroups()).extracting(membership -> membership.getGroupId())
                .containsExactlyInAnyOrder(lakeside.getId(), north.getId());
        assertThat(tomForm.getPrivileges()).singleElement()
                .satisfies(privilege -> assertThat(privilege.getAdmittingPhysicianId()).isEqualTo(priya.getId()));
        Long groupPolicy = policies.findByCarrierNameAndPolicyNumber("MedPro", "GX-1").orElseThrow().getId();
        assertThat(tomForm.getClaims()).singleElement()
                .satisfies(claim -> assertThat(claim.getPolicyKey()).isEqualTo("p" + groupPolicy));
        assertThat(tomForm.getCharges()).hasSize(1);

        Payer aetna = payers.findByNameIgnoreCase("aetna").orElseThrow();
        assertThat(aetna.getNote()).isEqualTo("Commercial");
        assertThat(enrollments.findForGroup(lakeside.getId())).singleElement().satisfies(e -> {
            assertThat(e.getPayer().getId()).isEqualTo(aetna.getId());
            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(e.getPayerAssignedId()).isEqualTo("G-88213");
            assertThat(e.getAccountRep().getName()).isEqualTo("Jane Doe");
            assertThat(e.getAccountRep().getGroup().getId()).isEqualTo(lakeside.getId());
        });
        assertThat(enrollments.findForGroup(north.getId())).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
            assertThat(e.getNotes()).isEqualTo("Waiting on the W-9");
            assertThat(e.getAccountRep()).isNull();
        });
        assertThat(priyaForm.getPayers()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.SUBMITTED));
        assertThat(tomForm.getPayers()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.NOT_STARTED));
        assertThat(contacts.findForProvider(priya.getId())).singleElement()
                .satisfies(c -> assertThat(c.getRole()).isEqualTo("Provider's analyst"));
        assertThat(contacts.findGeneralContacts(payers.findByNameIgnoreCase("Cigna").orElseThrow().getId()))
                .extracting(PayerContact::getName).containsExactly("Credentialing line");
    }

    @Test
    void aPayerAlreadyInCredAppIsUsedRatherThanDuplicated() {
        Payer existing = payers.save(new Payer("Aetna"));

        ImportReport report = imports.importFile(new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(PAYERS, "Payer ID", "PAY1", "Name", "AETNA")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1")
                .bytes());

        assertThat(report.problems()).isEmpty();
        assertThat(report.payers()).containsExactly("AETNA (PAY1, already in CredApp)");
        assertThat(payers.count()).isEqualTo(1);
        Group group = groups.findByTaxId("123456789").getFirst();
        assertThat(enrollments.findForGroup(group.getId())).singleElement()
                .satisfies(e -> assertThat(e.getPayer().getId()).isEqualTo(existing.getId()));
    }

    @Test
    void payerMistakesArePinnedToTheirCell() {
        ImportReport report = imports.preview(new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(GROUPS, "Group ID", "G2", "Legal business name", "North Surgery PC", "Tax ID", "987654321")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah")
                .row(PAYERS, "Payer ID", "PAY1", "Name", "Aetna")
                .row(PAYERS, "Payer ID", "PAY2", "Name", "Cigna")
                .row(PAYERS, "Payer ID", "PAY3", "Name", "aetna")
                .row(PAYER_CONTACTS, "Contact ID", "C1", "Payer ID", "PAY2", "Role", "Rep")
                .row(PAYER_CONTACTS, "Contact ID", "C2", "Payer ID", "PAY1", "Group ID", "G2", "Role", "Rep")
                .row(PAYER_CONTACTS, "Payer ID", "PAY1", "Group ID", "G1", "Provider ID", "P1", "Role", "Rep")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY1", "Account rep ID", "C1")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY2", "Status", "Active")
                .row(GROUP_PAYERS, "Group ID", "G1", "Payer ID", "PAY2")
                .row(GROUP_PAYERS, "Group ID", "G2", "Payer ID", "PAY1", "Account rep ID", "C2")
                .row(GROUP_PAYERS, "Group ID", "G2", "Payer ID", "PAY2", "Account rep ID", "C9")
                .row(PROVIDER_PAYERS, "Provider ID", "P1", "Payer ID", "PAY9", "Status", "Sometimes")
                .bytes());

        assertThat(report.problems()).extracting(ImportProblem::sheet, ImportProblem::row, ImportProblem::column,
                        ImportProblem::message)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("Payers", 4, "Name",
                                "Same payer as row 2. List each payer once; other sheets point at its Payer ID."),
                        org.assertj.core.groups.Tuple.tuple("Payer Contacts", 4, "Provider ID",
                                "A contact can be tied to a group or a provider, not both"),
                        org.assertj.core.groups.Tuple.tuple("Group Payers", 2, "Account rep ID",
                                "C1 is a contact of PAY2, not PAY1"),
                        org.assertj.core.groups.Tuple.tuple("Group Payers", 3, null,
                                "Add the effective date for an active enrollment"),
                        org.assertj.core.groups.Tuple.tuple("Group Payers", 4, "Payer ID",
                                "G1 is already enrolled with PAY2 on row 3"),
                        org.assertj.core.groups.Tuple.tuple("Group Payers", 6, "Account rep ID",
                                "\"C9\" isn't a Contact ID on the Payer Contacts sheet"),
                        org.assertj.core.groups.Tuple.tuple("Provider Payers", 2, "Payer ID",
                                "\"PAY9\" isn't a Payer ID on the Payers sheet"),
                        org.assertj.core.groups.Tuple.tuple("Provider Payers", 2, "Status",
                                "\"Sometimes\" isn't one of: Not started, In progress, Submitted, Active, Denied, Terminated"));
        // C2 is G2's own contact, so it's a fine rep for G2.
        assertThat(report.problems()).noneMatch(p -> "Group Payers".equals(p.sheet()) && Integer.valueOf(5).equals(p.row()));
        assertThat(payers.count()).isZero();
    }

    @Test
    void mistakesArePinnedToTheirCellAndNothingIsSaved() {
        byte[] file = new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "12345")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah",
                        "Date of birth", "next Tuesday", "Sex", "Sometimes")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Tom")
                .row(GROUP_MEMBERS, "Provider ID", "P9", "Group ID", "G1")
                .row(LICENSES, "Provider ID", "P1", "State", "OH", "License number", "L-1", "License type", "MD")
                .row(PROVIDER_SPECIALTIES, "Provider ID", "P1", "Taxonomy code", "NOT-A-CODE")
                .rawSheet("Notes", List.of("Anything"), List.of(List.of("call Dr. Shah back")))
                .bytes();

        ImportReport report = imports.importFile(file);

        assertThat(report.saved()).isFalse();
        assertThat(report.problems()).extracting(ImportProblem::sheet, ImportProblem::row, ImportProblem::column,
                        ImportProblem::message)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("Groups", 2, "Tax ID", "Tax ID must be exactly 9 digits"),
                        org.assertj.core.groups.Tuple.tuple("Providers", 2, "Date of birth",
                                "\"next Tuesday\" isn't a date. Use 2025-01-31 or 1/31/2025"),
                        org.assertj.core.groups.Tuple.tuple("Providers", 2, "Sex",
                                "\"Sometimes\" isn't one of: Male, Female, Other, Unknown"),
                        org.assertj.core.groups.Tuple.tuple("Providers", 3, "Provider ID",
                                "P1 is already used on row 2"),
                        org.assertj.core.groups.Tuple.tuple("Providers", 3, "Last name", "Last name is required"),
                        org.assertj.core.groups.Tuple.tuple("Group Members", 2, "Provider ID",
                                "\"P9\" isn't a Provider ID on the Providers sheet"),
                        org.assertj.core.groups.Tuple.tuple("Licenses", 2, "Expiration date",
                                "Expiration date is required"),
                        org.assertj.core.groups.Tuple.tuple("Provider Specialties", 2, "Taxonomy code",
                                "NOT-A-CODE isn't a taxonomy code CredApp knows"));
        assertThat(report.problems()).anySatisfy(problem -> {
            assertThat(problem.sheet()).isEqualTo("Notes");
            assertThat(problem.message()).contains("isn't part of the template");
        });
        assertThat(providers.count()).isZero();
        assertThat(groups.count()).isZero();
    }

    @Test
    void aRuleOnlyTheSaveCatchesStillRollsEverythingBack() {
        byte[] file = new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "Tax ID", "123456789")
                .row(OWNERS, "Owner ID", "O1", "First name", "Ann", "Last name", "Lee")
                .row(OWNERS, "Owner ID", "O2", "First name", "Bob", "Last name", "Lee")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O1", "Percent owned", "70")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O2", "Percent owned", "50%")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah")
                .bytes();

        ImportReport report = imports.importFile(file);

        assertThat(report.saved()).isFalse();
        assertThat(report.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.sheet()).isEqualTo("Ownership");
            assertThat(problem.row()).isEqualTo(3);
            assertThat(problem.column()).isEqualTo("Percent owned");
            assertThat(problem.message()).isEqualTo("Owners add up to 120%; the total can't pass 100%");
        });
        assertThat(owners.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(providers.count()).isZero();
    }

    @Test
    void recordsAlreadyInCredAppAreFlaggedRatherThanDuplicated() {
        imports.importFile(new TestWorkbook()
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah", "NPI", "1111111111")
                .bytes());

        ImportReport again = imports.preview(new TestWorkbook()
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah", "NPI", "1111111111")
                .bytes());

        assertThat(again.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("NPI");
            assertThat(problem.message()).isEqualTo("A provider with NPI 1111111111 is already in CredApp");
        });
        assertThat(providers.count()).isEqualTo(1);
    }

    @Test
    void somethingThatIsntAWorkbookSaysSo() {
        ImportReport report = imports.preview("name,npi\nPriya,1111111111".getBytes(StandardCharsets.UTF_8));

        assertThat(report.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.sheet()).isNull();
            assertThat(problem.message()).startsWith("This isn't an Excel workbook (.xlsx)");
        });
    }

    @Test
    void theTemplateReadsBackCleanAndEmpty() {
        ImportReport report = imports.preview(OnboardingTemplate.workbook());

        assertThat(report.problems()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).startsWith("There's nothing to import"));
    }

    /** Two groups and two providers, with a row on every sheet. */
}
