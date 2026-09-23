package dev.bryrich.credapp.group;

import dev.bryrich.credapp.payer.PayerService;
import dev.bryrich.credapp.payer.enrollment.PayerEnrollmentService;
import dev.bryrich.credapp.tracking.TrackingService;
import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.group.membership.GroupProviderForm;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.owner.GroupOwnershipService;
import dev.bryrich.credapp.owner.OwnerService;
import dev.bryrich.credapp.provider.ProviderService;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyForm;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyService;
import dev.bryrich.credapp.taxonomy.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static dev.bryrich.credapp.common.WebTestSupport.coordinator;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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

/** The single group form, rendered through the real template. */
@WebMvcTest(GroupWebController.class)
class GroupWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private GroupService groupService;
    @MockitoBean private GroupProfileService profileService;
    @MockitoBean private GroupLocationService locationService;
    @MockitoBean private GroupOwnershipService ownershipService;
    @MockitoBean private GroupProviderService groupProviderService;
    @MockitoBean private OwnerService ownerService;
    @MockitoBean private ProviderService providerService;
    @MockitoBean private GroupTaxonomyService groupTaxonomyService;
    @MockitoBean private TaxonomyService taxonomyService;
    @MockitoBean private MalpracticePolicyService policyService;
    @MockitoBean private PayerService payerService;
    @MockitoBean private PayerEnrollmentService enrollmentService;

    @MockitoBean
    private TrackingService trackingService;

    private final Group group = group(1L);

    @BeforeEach
    void setUp() {
        when(groupService.findById(1L)).thenReturn(group);
        when(ownershipService.totalPercentOwned(1L)).thenReturn(BigDecimal.ZERO);
        when(ownershipService.remainingPercent(1L)).thenReturn(new BigDecimal("100"));
    }

    @Test
    void theNewFormRendersEverySectionEmpty() throws Exception {
        mockMvc.perform(get("/groups/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/form"))
                .andExpect(content().string(containsString("data-section=\"owners\"")))
                .andExpect(content().string(containsString("data-section=\"relations\"")));
    }

    @Test
    void theEditFormRendersSavedRowsOfEveryKind() throws Exception {
        when(profileService.load(1L)).thenReturn(formWithOneOfEachRow());

        mockMvc.perform(get("/groups/1/edit").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/form"))
                .andExpect(content().string(containsString("name=\"owners[0].ownerId\"")))
                .andExpect(content().string(containsString("name=\"relations[0].ownerKey\"")));
    }

    @Test
    void createSavesOnceAndRedirectsToTheNewGroup() throws Exception {
        when(profileService.save(isNull(), any())).thenReturn(group(7L));

        mockMvc.perform(post("/groups").with(coordinator()).with(csrf())
                        .param("details.lbn", "Harbor Pediatrics")
                        .param("details.taxId", "555666777")
                        .param("owners[0].key", "new-1")
                        .param("owners[0].mode", "new")
                        .param("owners[0].newOwner.firstName", "Sam")
                        .param("owners[0].newOwner.lastName", "Newperson")
                        .param("owners[0].percentOwned", "100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/7"));

        verify(profileService).save(isNull(), any(GroupProfileForm.class));
    }

    /**
     * A row picking an existing owner has no newOwner object, and the error lookups for the
     * hidden new-owner fields must not trip over that when the page comes back.
     */
    @Test
    void anExistingOwnerRowWithAnErrorRendersWithoutANewOwner() throws Exception {
        mockMvc.perform(post("/groups").with(coordinator()).with(csrf())
                        .param("details.lbn", "Harbor Pediatrics")
                        .param("details.taxId", "555666777")
                        .param("owners[0].key", "o3")
                        .param("owners[0].mode", "existing")
                        .param("owners[0].ownerId", "3")
                        .param("owners[0].percentOwned", "500"))
                .andExpect(status().isOk())
                .andExpect(view().name("group/form"))
                .andExpect(model().attributeHasFieldErrors("form", "owners[0].percentOwned"))
                .andExpect(content().string(containsString("Percent owned cannot exceed 100")));

        verify(profileService, never()).save(any(), any());
    }

    @Test
    void aNewOwnerMissingANameComesBackMarkedWithoutEchoingTheSsn() throws Exception {
        mockMvc.perform(post("/groups").with(coordinator()).with(csrf())
                        .param("details.lbn", "Harbor Pediatrics")
                        .param("details.taxId", "555666777")
                        .param("owners[0].key", "new-1")
                        .param("owners[0].mode", "new")
                        .param("owners[0].newOwner.firstName", "Sam")
                        .param("owners[0].newOwner.ssn", "123456789"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "owners[0].newOwner.lastName"))
                .andExpect(content().string(not(containsString("123456789"))));
    }

    @Test
    void theDetailPageLinksToTheOneForm() throws Exception {
        mockMvc.perform(get("/groups/1").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/detail"))
                .andExpect(content().string(containsString("/groups/1/edit")))
                .andExpect(content().string(not(containsString("/groups/1/owners/new"))));
    }

    private static Group group(Long id) {
        Group group = new Group("Northside Health", "123456789");
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private static GroupProfileForm formWithOneOfEachRow() {
        GroupProfileForm form = new GroupProfileForm();
        form.getDetails().setLbn("Northside Health");
        form.getDetails().setTaxId("123456789");
        form.getLocations().add(new GroupProfileForm.LocationRow());
        GroupProfileForm.OwnerRow owner = new GroupProfileForm.OwnerRow();
        owner.setKey("o3");
        owner.setOwnerId(3L);
        form.getOwners().add(owner);
        GroupProfileForm.RelationRow relation = new GroupProfileForm.RelationRow();
        relation.setOwnerKey("o3");
        form.getRelations().add(relation);
        form.getProviders().add(new GroupProviderForm());
        form.getTaxonomies().add(new GroupTaxonomyForm());
        form.getPolicies().add(new GroupProfileForm.PolicyRow());
        return form;
    }
}
