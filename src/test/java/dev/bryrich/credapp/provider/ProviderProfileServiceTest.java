package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.license.LicenseService;
import dev.bryrich.credapp.malpractice.CoverageScope;
import dev.bryrich.credapp.malpractice.MalpracticeClaim;
import dev.bryrich.credapp.malpractice.MalpracticeClaimService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.provider.ProviderProfileForm.ClaimRow;
import dev.bryrich.credapp.provider.ProviderProfileForm.LicenseRow;
import dev.bryrich.credapp.provider.ProviderProfileForm.PolicyRow;
import dev.bryrich.credapp.provider.location.PcpScp;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.location.ProviderLocationService;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomy;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the single provider form's save against Postgres, since the point of it is the order
 * of writes: the partial unique index on primary specialties, the composite keys on
 * practice locations, and claims pointing at policies saved in the same request.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProviderProfileServiceTest {

    private static final String ADDICTION = "207QA0401X";
    private static final String ANESTHESIOLOGY = "207L00000X";

    @Autowired
    private ProviderProfileService profileService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private ProviderTaxonomyService taxonomyService;

    @Autowired
    private ProviderLocationService locationService;

    @Autowired
    private MalpracticeClaimService claimService;

    @Autowired
    private MalpracticePolicyService policyService;

    private Group group;
    private GroupLocation location;

    @BeforeEach
    void setUp() {
        group = new Group("Northside Health", "123456789");
        entityManager.persist(group);
        location = new GroupLocation("Main Clinic", "1 Main St");
        group.addLocation(location);
        entityManager.persist(location);
        entityManager.flush();
    }

    @Test
    void oneSaveCreatesTheProviderAndEveryList() {
        ProviderProfileForm form = baseForm();
        form.getGroups().add(groupRow(group.getId()));
        form.getLocations().add(locationRow(location.getId()));
        form.getTaxonomies().add(taxonomyRow(ADDICTION, true));
        form.getTaxonomies().add(taxonomyRow(ANESTHESIOLOGY, false));
        form.getLicenses().add(licenseRow("A-1"));
        form.getPolicies().add(policyRow("new-1", "MP-100"));
        form.getClaims().add(claimRow("C-9", "new-1"));

        Provider saved = save(null, form);

        assertThat(licenseService.findByProviderId(saved.getId())).hasSize(1);
        assertThat(locationService.findByProviderId(saved.getId())).hasSize(1);
        assertThat(primaryCode(saved.getId())).isEqualTo(ADDICTION);
        List<MalpracticeClaim> claims = claimService.findByProviderId(saved.getId());
        assertThat(claims).singleElement()
                .extracting(claim -> claim.getPolicy().getPolicyNumber())
                .isEqualTo("MP-100");
    }

    @Test
    void anEditDeletesRowsLeftOffAndUpdatesTheRest() {
        ProviderProfileForm form = baseForm();
        form.getLicenses().add(licenseRow("A-1"));
        form.getLicenses().add(licenseRow("B-2"));
        Provider saved = save(null, form);

        ProviderProfileForm edit = profileService.load(saved.getId());
        edit.getLicenses().removeIf(row -> row.getLicenseNumber().equals("A-1"));
        edit.getLicenses().getFirst().setLicenseNumber("B-2-renewed");
        save(saved.getId(), edit);

        assertThat(licenseService.findByProviderId(saved.getId()))
                .extracting(license -> license.getLicenseNumber())
                .containsExactly("B-2-renewed");
    }

    @Test
    void movingThePrimarySpecialtyDoesNotTripTheOnePrimaryIndex() {
        ProviderProfileForm form = baseForm();
        form.getTaxonomies().add(taxonomyRow(ADDICTION, true));
        form.getTaxonomies().add(taxonomyRow(ANESTHESIOLOGY, false));
        Provider saved = save(null, form);

        ProviderProfileForm edit = profileService.load(saved.getId());
        edit.getTaxonomies().forEach(row -> row.setPrimary(row.getCode().equals(ANESTHESIOLOGY)));
        save(saved.getId(), edit);

        assertThat(primaryCode(saved.getId())).isEqualTo(ANESTHESIOLOGY);
    }

    @Test
    void removingAGroupAndItsLocationTogetherWorks() {
        ProviderProfileForm form = baseForm();
        form.getGroups().add(groupRow(group.getId()));
        form.getLocations().add(locationRow(location.getId()));
        Provider saved = save(null, form);

        ProviderProfileForm edit = profileService.load(saved.getId());
        edit.getGroups().clear();
        edit.getLocations().clear();
        save(saved.getId(), edit);

        assertThat(locationService.findByProviderId(saved.getId())).isEmpty();
    }

    @Test
    void aClaimCanBeUnlinkedFromAPolicyRemovedInTheSameSave() {
        ProviderProfileForm form = baseForm();
        form.getPolicies().add(policyRow("new-1", "MP-100"));
        form.getClaims().add(claimRow("C-9", "new-1"));
        Provider saved = save(null, form);

        ProviderProfileForm edit = profileService.load(saved.getId());
        edit.getPolicies().clear();
        edit.getClaims().getFirst().setPolicyKey(null);
        save(saved.getId(), edit);

        assertThat(policyService.findByProviderId(saved.getId())).isEmpty();
        assertThat(claimService.findByProviderId(saved.getId())).singleElement()
                .extracting(MalpracticeClaim::getPolicy)
                .isNull();
    }

    @Test
    void validateFlagsALocationWhoseGroupIsNotListed() {
        ProviderProfileForm form = baseForm();
        form.getLocations().add(locationRow(location.getId()));

        BindingResult errors = validate(null, form);

        assertThat(errors.getFieldError("locations[0].locationId")).isNotNull();
    }

    @Test
    void validateFlagsASecondPrimaryAndARepeatedGroup() {
        ProviderProfileForm form = baseForm();
        form.getGroups().add(groupRow(group.getId()));
        form.getGroups().add(groupRow(group.getId()));
        form.getTaxonomies().add(taxonomyRow(ADDICTION, true));
        form.getTaxonomies().add(taxonomyRow(ANESTHESIOLOGY, true));

        BindingResult errors = validate(null, form);

        assertThat(errors.getFieldError("groups[1].groupId")).isNotNull();
        assertThat(errors.getFieldError("taxonomies[1].primary")).isNotNull();
    }

    @Test
    void validateFlagsAClaimStillPointingAtARemovedPolicy() {
        ProviderProfileForm form = baseForm();
        form.getPolicies().add(policyRow("new-1", "MP-100"));
        form.getClaims().add(claimRow("C-9", "new-1"));
        Provider saved = save(null, form);

        ProviderProfileForm edit = profileService.load(saved.getId());
        edit.getPolicies().clear();

        BindingResult errors = validate(saved.getId(), edit);

        assertThat(errors.getFieldError("claims[0].policyKey")).isNotNull();
    }

    @Test
    void validateSkipsNullRowsLeftByAnIndexGap() {
        ProviderProfileForm form = baseForm();
        form.getLicenses().add(null);
        form.getLicenses().add(licenseRow("A-1"));

        assertThat(validate(null, form).hasErrors()).isFalse();
    }

    // ============ helpers ============

    private Provider save(Long id, ProviderProfileForm form) {
        assertThat(validate(id, form).getAllErrors()).isEmpty();
        Provider saved = profileService.save(id, form);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private BindingResult validate(Long id, ProviderProfileForm form) {
        BindingResult errors = new BeanPropertyBindingResult(form, "form");
        profileService.validate(id, form, errors);
        return errors;
    }

    private String primaryCode(Long providerId) {
        return taxonomyService.findByProviderId(providerId).stream()
                .filter(ProviderTaxonomy::isPrimary)
                .map(row -> row.getTaxonomy().getCode())
                .findFirst()
                .orElse(null);
    }

    private static ProviderProfileForm baseForm() {
        ProviderProfileForm form = new ProviderProfileForm();
        form.getDetails().setFirstName("Ada");
        form.getDetails().setLastName("Lovelace");
        return form;
    }

    private static ProviderGroupForm groupRow(Long groupId) {
        ProviderGroupForm row = new ProviderGroupForm();
        row.setGroupId(groupId);
        return row;
    }

    private static ProviderLocationForm locationRow(Long locationId) {
        ProviderLocationForm row = new ProviderLocationForm();
        row.setLocationId(locationId);
        row.setPcpScp(PcpScp.PCP);
        return row;
    }

    private static ProviderTaxonomyForm taxonomyRow(String code, boolean primary) {
        ProviderTaxonomyForm row = new ProviderTaxonomyForm();
        row.setCode(code);
        row.setPrimary(primary);
        return row;
    }

    private static LicenseRow licenseRow(String number) {
        LicenseRow row = new LicenseRow();
        row.setState("MI");
        row.setLicenseNumber(number);
        row.setLicenseType("Professional");
        row.setExpirationDate(LocalDate.now().plusYears(1));
        return row;
    }

    private static PolicyRow policyRow(String key, String number) {
        PolicyRow row = new PolicyRow();
        row.setKey(key);
        row.setCarrierName("MedPro");
        row.setPolicyNumber(number);
        row.setTypeOfCoverage("Claims-made");
        row.setEffectiveDate(LocalDate.of(2025, 1, 1));
        row.setSharedIndividual(CoverageScope.INDIVIDUAL);
        return row;
    }

    private static ClaimRow claimRow(String number, String policyKey) {
        ClaimRow row = new ClaimRow();
        row.setCarrierName("MedPro");
        row.setClaimNumber(number);
        row.setPolicyKey(policyKey);
        return row;
    }
}
