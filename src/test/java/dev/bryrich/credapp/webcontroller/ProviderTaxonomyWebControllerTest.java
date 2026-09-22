package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.Taxonomy;
import dev.bryrich.credapp.service.ProviderService;
import dev.bryrich.credapp.service.ProviderTaxonomyService;
import dev.bryrich.credapp.service.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static dev.bryrich.credapp.webcontroller.WebTestSupport.coordinator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProviderTaxonomyWebController.class)
class ProviderTaxonomyWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderTaxonomyService providerTaxonomyService;

    @MockitoBean
    private TaxonomyService taxonomyService;

    @MockitoBean
    private ProviderService providerService;

    private final Provider provider = WebTestSupport.provider(1L, "Ada", "Byron");

    @BeforeEach
    void setUp() {
        when(providerService.findById(1L)).thenReturn(provider);
        when(taxonomyService.findAllForSelect()).thenReturn(
                List.of(new Taxonomy("207Q00000X", "Family Medicine", "Allopathic & Osteopathic Physicians")));
    }

    @Test
    void newTaxonomyOffersTheSeededCodes() throws Exception {
        mockMvc.perform(get("/providers/1/taxonomies/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/taxonomy-form"))
                .andExpect(model().attributeExists("taxonomies", "form"));
    }

    @Test
    void theFormStillRendersWhenNoCodesAreSeeded() throws Exception {
        when(taxonomyService.findAllForSelect()).thenReturn(List.of());

        mockMvc.perform(get("/providers/1/taxonomies/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/taxonomy-form"));
    }

    @Test
    void createAssignsTheCodeAndRedirects() throws Exception {
        mockMvc.perform(post("/providers/1/taxonomies")
                        .with(coordinator())
                        .with(csrf())
                        .param("code", "207Q00000X")
                        .param("primary", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(providerTaxonomyService).assign(1L, "207Q00000X", true);
    }

    @Test
    void createRedisplaysTheFormWhenNoCodeIsChosen() throws Exception {
        mockMvc.perform(post("/providers/1/taxonomies")
                        .with(coordinator())
                        .with(csrf())
                        .param("code", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/taxonomy-form"))
                .andExpect(model().attributeHasFieldErrors("form", "code"));

        verify(providerTaxonomyService, never()).assign(any(), anyString(), anyBoolean());
    }

    @Test
    void makePrimaryReassignsTheSameCodeAsPrimary() throws Exception {
        mockMvc.perform(post("/providers/1/taxonomies/207Q00000X/primary")
                        .with(coordinator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(providerTaxonomyService).assign(1L, "207Q00000X", true);
    }

    @Test
    void deleteUnassignsTheCode() throws Exception {
        mockMvc.perform(post("/providers/1/taxonomies/207Q00000X/delete")
                        .with(coordinator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(providerTaxonomyService).unassign(1L, "207Q00000X");
    }
}
