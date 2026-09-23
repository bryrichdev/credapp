package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.GroupProfileService;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyRepository;
import dev.bryrich.credapp.owner.OwnerRepository;
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
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP_SPECIALTIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.HOSPITAL_PRIVILEGES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LICENSES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNERSHIP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNER_RELATIONSHIPS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICIES;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PRACTICE_LOCATIONS;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDERS;
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
        ImportReport report = imports.preview(fullPractice().bytes());

        assertThat(report.problems()).isEmpty();
        assertThat(report.saved()).isFalse();
        assertThat(report.groups()).containsExactly("Lakeside Clinic LLC (G1)", "North Surgery PC (G2)");
        assertThat(report.providers()).containsExactly("Shah, Priya (P1)", "Ng, Tom (P2)");
        assertThat(report.counts()).extracting(ImportReport.SheetCount::sheet).hasSize(17);
        assertThat(providers.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(owners.count()).isZero();
    }

    @Test
    void anImportCreatesEverySectionLinkedUp() {
        ImportReport report = imports.importFile(fullPractice().bytes());

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
    private static TestWorkbook fullPractice() {
        return new TestWorkbook()
                .row(GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC", "DBA", "Lakeside",
                        "NPI", "1234567893", "Tax ID", "12-3456789")
                .row(GROUPS, "Group ID", "g2", "Legal business name", "North Surgery PC", "Tax ID", "987654321")
                .row(GROUP_LOCATIONS, "Location ID", "L1", "Group ID", "G1", "Location name", "Main office",
                        "Address", "1 Main St, Toledo, OH 43604", "Phone", "419-555-0100")
                .row(GROUP_LOCATIONS, "Location ID", "L2", "Group ID", "G2", "Location name", "North",
                        "Address", "5 North Rd, Toledo, OH 43612")
                .row(OWNERS, "Owner ID", "O1", "First name", "Ann", "Last name", "Lee",
                        "Date of birth", "2/3/1970", "SSN", "123-45-6789")
                .row(OWNERS, "Owner ID", "O2", "First name", "Bob", "Last name", "Lee")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O1", "Percent owned", "60",
                        "Effective date", "2020-01-01")
                .row(OWNERSHIP, "Group ID", "G1", "Owner ID", "O2", "Percent owned", "40")
                .row(OWNERSHIP, "Group ID", "G2", "Owner ID", "O1", "Percent owned", "100")
                .row(OWNER_RELATIONSHIPS, "Group ID", "G1", "Owner ID", "O1", "Relationship", "Spouse",
                        "Related owner ID", "O2")
                .row(GROUP_SPECIALTIES, "Group ID", "G1", "Taxonomy code", "207q00000x", "Primary", "Yes")
                .row(PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah",
                        "Date of birth", "1980-05-17", "Sex", "F", "NPI", "1111111111", "Email", "priya@example.com",
                        "ZIP", "02134", "US citizen", "Y", "Languages", "English, Hindi")
                .row(PROVIDERS, "Provider ID", "P2", "First name", "Tom", "Last name", "Ng", "NPI", "2222222222")
                .row(GROUP_MEMBERS, "Provider ID", "P1", "Group ID", "G1", "Effective date", "3/1/2021")
                .row(GROUP_MEMBERS, "Provider ID", "P2", "Group ID", "G1")
                .row(GROUP_MEMBERS, "Provider ID", "P2", "Group ID", "G2")
                .row(PRACTICE_LOCATIONS, "Provider ID", "P1", "Location ID", "L1", "PCP or SCP", "PCP")
                .row(PRACTICE_LOCATIONS, "Provider ID", "P2", "Location ID", "L2", "PCP or SCP", "Specialist")
                .row(PROVIDER_SPECIALTIES, "Provider ID", "P1", "Taxonomy code", "207QA0401X", "Primary", "Yes")
                .row(PROVIDER_SPECIALTIES, "Provider ID", "P2", "Taxonomy code", "207L00000X")
                .row(LICENSES, "Provider ID", "P1", "State", "oh", "License number", "35.123456", "License type", "MD",
                        "Expiration date", "2027-01-31", "Status", "active")
                .row(LICENSES, "Provider ID", "P2", "State", "OH", "License number", "35.654321", "License type", "MD",
                        "Expiration date", "2026-12-31")
                .row(CERTIFICATIONS, "Provider ID", "P1", "Board", "American Board of Family Medicine",
                        "Effective date", "2015-07-01")
                .row(HOSPITAL_PRIVILEGES, "Provider ID", "P2", "Hospital", "St. Vincent", "Status", "Courtesy",
                        "Admitting provider ID", "P1")
                .row(POLICIES, "Policy ID", "POL1", "Provider ID", "P1", "Policy number", "PX-1", "Carrier", "MedPro",
                        "Type of coverage", "Claims-made", "Effective date", "2024-01-01",
                        "Expiration date", "2025-01-01", "Coverage per occurrence", "$1,000,000",
                        "Shared or individual", "Individual")
                .row(POLICIES, "Policy ID", "POL2", "Group ID", "G1", "Policy number", "GX-1", "Carrier", "MedPro",
                        "Type of coverage", "Occurrence", "Effective date", "2024-01-01",
                        "Shared or individual", "Shared")
                .row(CLAIMS, "Provider ID", "P1", "Claim number", "C-1", "Carrier", "MedPro", "Policy ID", "POL1",
                        "Outcome", "Closed without payment")
                .row(CLAIMS, "Provider ID", "P2", "Claim number", "C-2", "Carrier", "MedPro", "Policy ID", "pol2")
                .row(REFERENCES, "Provider ID", "P1", "Name", "Dr. Jane Kim", "Relationship", "Colleague",
                        "Email", "jane.kim@example.com")
                .row(DISCLOSURES, "Provider ID", "P2", "Classification", "Misdemeanor", "Status", "Dismissed",
                        "Incident date", "2010-01-01");
    }
}
