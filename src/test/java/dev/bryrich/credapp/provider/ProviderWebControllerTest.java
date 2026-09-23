package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.payer.PayerService;
import dev.bryrich.credapp.payer.enrollment.PayerEnrollmentService;
import dev.bryrich.credapp.common.WebTestSupport;
import dev.bryrich.credapp.group.GroupService;
import dev.bryrich.credapp.group.location.GroupLocationRepository;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.license.LicenseService;
import dev.bryrich.credapp.malpractice.MalpracticeClaimService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.provider.certification.CertificationService;
import dev.bryrich.credapp.provider.disclosure.CriminalChargeService;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.location.ProviderLocationService;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilegeService;
import dev.bryrich.credapp.provider.reference.ProviderReferenceService;
import dev.bryrich.credapp.ssn.SsnAccessService;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyService;
import dev.bryrich.credapp.taxonomy.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static dev.bryrich.credapp.common.WebTestSupport.coordinator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * The single provider form. These render the real template, so a broken expression in any
 * row fragment fails here rather than in the browser.
 */
@WebMvcTest(ProviderWebController.class)
class ProviderWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ProviderService providerService;
    @MockitoBean private ProviderProfileService profileService;
    @MockitoBean private LicenseService licenseService;
    @MockitoBean private GroupProviderService groupProviderService;
    @MockitoBean private GroupService groupService;
    @MockitoBean private GroupLocationRepository groupLocationRepository;
    @MockitoBean private TaxonomyService taxonomyService;
    @MockitoBean private SsnAccessService ssnAccessService;
    @MockitoBean private ProviderTaxonomyService providerTaxonomyService;
    @MockitoBean private ProviderLocationService providerLocationService;
    @MockitoBean private CertificationService certificationService;
    @MockitoBean private ProviderReferenceService referenceService;
    @MockitoBean private HospitalPrivilegeService privilegeService;
    @MockitoBean private CriminalChargeService chargeService;
    @MockitoBean private MalpracticePolicyService policyService;
    @MockitoBean private MalpracticeClaimService claimService;
    @MockitoBean private PayerService payerService;
    @MockitoBean private PayerEnrollmentService enrollmentService;

    private final Provider provider = WebTestSupport.provider(1L, "Ada", "Byron");

    @BeforeEach
    void setUp() {
        when(providerService.findById(1L)).thenReturn(provider);
    }

    @Test
    void theNewFormRendersEverySectionEmpty() throws Exception {
        mockMvc.perform(get("/providers/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/form"))
                .andExpect(content().string(containsString("data-section=\"licenses\"")))
                .andExpect(content().string(containsString("data-section=\"charges\"")));
    }

    @Test
    void theEditFormRendersSavedRowsOfEveryKind() throws Exception {
        when(profileService.load(1L)).thenReturn(formWithOneOfEachRow());

        mockMvc.perform(get("/providers/1/edit").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/form"))
                .andExpect(content().string(containsString("name=\"licenses[0].licenseNumber\"")))
                .andExpect(content().string(containsString("name=\"claims[0].policyKey\"")));
    }

    @Test
    void createSavesOnceAndRedirectsToTheNewProvider() throws Exception {
        when(profileService.save(isNull(), any())).thenReturn(WebTestSupport.provider(9L, "Ada", "Lovelace"));

        mockMvc.perform(post("/providers").with(coordinator()).with(csrf())
                        .param("details.firstName", "Ada")
                        .param("details.lastName", "Lovelace")
                        .param("licenses[0].state", "MI")
                        .param("licenses[0].licenseNumber", "A-1")
                        .param("licenses[0].licenseType", "Professional")
                        .param("licenses[0].expirationDate", "2030-01-31")
                        .param("licenses[0].status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/9"));

        verify(profileService).save(isNull(), any(ProviderProfileForm.class));
    }

    @Test
    void aRowMissingARequiredFieldComesBackMarkedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/providers").with(coordinator()).with(csrf())
                        .param("details.firstName", "Ada")
                        .param("details.lastName", "Lovelace")
                        .param("licenses[0].state", "MI"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/form"))
                .andExpect(model().attributeHasFieldErrors("form", "licenses[0].licenseNumber"))
                .andExpect(content().string(containsString("License number is required")));

        verify(profileService, never()).save(any(), any());
    }

    @Test
    void aConflictOnSaveComesBackWithAMessage() throws Exception {
        when(profileService.save(eq(1L), any())).thenThrow(new DataIntegrityViolationException("dup"));

        mockMvc.perform(post("/providers/1/edit").with(coordinator()).with(csrf())
                        .param("details.firstName", "Ada")
                        .param("details.lastName", "Byron"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/form"))
                .andExpect(content().string(containsString("Nothing was saved")));
    }

    @Test
    void theDetailPageLinksToTheOneForm() throws Exception {
        mockMvc.perform(get("/providers/1").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/detail"))
                .andExpect(content().string(containsString("/providers/1/edit")))
                .andExpect(content().string(not(containsString("/providers/1/licenses/new"))));
    }

    private static ProviderProfileForm formWithOneOfEachRow() {
        ProviderProfileForm form = new ProviderProfileForm();
        form.getDetails().setFirstName("Ada");
        form.getDetails().setLastName("Byron");
        form.getGroups().add(new ProviderGroupForm());
        form.getLocations().add(new ProviderLocationForm());
        form.getTaxonomies().add(new ProviderTaxonomyForm());
        form.getLicenses().add(new ProviderProfileForm.LicenseRow());
        form.getCertifications().add(new ProviderProfileForm.CertificationRow());
        form.getPrivileges().add(new ProviderProfileForm.PrivilegeRow());
        ProviderProfileForm.PolicyRow policy = new ProviderProfileForm.PolicyRow();
        policy.setKey("p5");
        form.getPolicies().add(policy);
        ProviderProfileForm.ClaimRow claim = new ProviderProfileForm.ClaimRow();
        claim.setPolicyKey("p5");
        form.getClaims().add(claim);
        form.getReferences().add(new ProviderProfileForm.ReferenceRow());
        form.getCharges().add(new ProviderProfileForm.ChargeRow());
        return form;
    }
}
