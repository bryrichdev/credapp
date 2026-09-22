package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import dev.bryrich.credapp.service.MalpracticePolicyService;
import dev.bryrich.credapp.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static dev.bryrich.credapp.webcontroller.WebTestSupport.coordinator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProviderMalpracticePolicyWebController.class)
class ProviderMalpracticePolicyWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MalpracticePolicyService policyService;

    @MockitoBean
    private ProviderService providerService;

    private final Provider provider = WebTestSupport.provider(1L, "Ada", "Byron");

    @BeforeEach
    void setUp() {
        when(providerService.findById(1L)).thenReturn(provider);
        MalpracticePolicy saved = MalpracticePolicy.forProvider(provider, "P-1", "Acme Mutual",
                "Claims-made", LocalDate.of(2026, 1, 1), CoverageScope.INDIVIDUAL);
        ReflectionTestUtils.setField(saved, "id", 9L);
        when(policyService.addForProvider(any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
    }

    @Test
    void createOwnsThePolicyByProviderAndRedirects() throws Exception {
        mockMvc.perform(post("/providers/1/policies")
                        .with(coordinator())
                        .with(csrf())
                        .param("providerId", "1")
                        .param("policyNumber", "P-1")
                        .param("carrierName", "Acme Mutual")
                        .param("typeOfCoverage", "Claims-made")
                        .param("sharedIndividual", "INDIVIDUAL")
                        .param("effectiveDate", "2026-01-01")
                        .param("expirationDate", "2027-01-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(policyService).addForProvider(eq(1L), eq("P-1"), eq("Acme Mutual"),
                eq("Claims-made"), eq(LocalDate.of(2026, 1, 1)), eq(CoverageScope.INDIVIDUAL));
    }

    @Test
    void aFormNamingBothOwnersIsRejectedBeforeItReachesTheService() throws Exception {
        mockMvc.perform(post("/providers/1/policies")
                        .with(coordinator())
                        .with(csrf())
                        .param("providerId", "1")
                        .param("groupId", "7")
                        .param("policyNumber", "P-1")
                        .param("carrierName", "Acme Mutual")
                        .param("typeOfCoverage", "Claims-made")
                        .param("sharedIndividual", "INDIVIDUAL")
                        .param("effectiveDate", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/policy-form"));

        verify(policyService, never()).addForProvider(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anExpirationBeforeTheEffectiveDateIsRejected() throws Exception {
        mockMvc.perform(post("/providers/1/policies")
                        .with(coordinator())
                        .with(csrf())
                        .param("providerId", "1")
                        .param("policyNumber", "P-1")
                        .param("carrierName", "Acme Mutual")
                        .param("typeOfCoverage", "Claims-made")
                        .param("sharedIndividual", "INDIVIDUAL")
                        .param("effectiveDate", "2026-01-01")
                        .param("expirationDate", "2025-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/policy-form"));

        verify(policyService, never()).addForProvider(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteRemovesThePolicy() throws Exception {
        mockMvc.perform(post("/providers/1/policies/9/delete")
                        .with(coordinator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(policyService).delete(9L);
    }
}
