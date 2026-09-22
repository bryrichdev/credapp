package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.Certification;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.service.CertificationService;
import dev.bryrich.credapp.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static dev.bryrich.credapp.webcontroller.WebTestSupport.coordinator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(ProviderCertificationWebController.class)
class ProviderCertificationWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificationService certificationService;

    @MockitoBean
    private ProviderService providerService;

    private final Provider provider = WebTestSupport.provider(1L, "Ada", "Byron");

    @BeforeEach
    void setUp() {
        when(providerService.findById(1L)).thenReturn(provider);
    }

    @Test
    void newCertificationRendersTheForm() throws Exception {
        mockMvc.perform(get("/providers/1/certifications/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/certification-form"))
                .andExpect(model().attributeExists("form", "provider"));
    }

    @Test
    void createSavesAndRedirectsBackToTheProvider() throws Exception {
        mockMvc.perform(post("/providers/1/certifications")
                        .with(coordinator())
                        .with(csrf())
                        .param("board", "American Board of Family Medicine")
                        .param("effectiveDate", "2020-01-01")
                        .param("expirationDate", "2030-01-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(certificationService).addCertification(eq(1L), any(Certification.class));
    }

    @Test
    void createRedisplaysTheFormWhenTheBoardIsMissing() throws Exception {
        mockMvc.perform(post("/providers/1/certifications")
                        .with(coordinator())
                        .with(csrf())
                        .param("board", "")
                        .param("effectiveDate", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/certification-form"))
                .andExpect(model().attributeHasFieldErrors("form", "board"));

        verify(certificationService, never()).addCertification(any(), any());
    }

    @Test
    void createRejectsAnExpirationBeforeTheEffectiveDate() throws Exception {
        mockMvc.perform(post("/providers/1/certifications")
                        .with(coordinator())
                        .with(csrf())
                        .param("board", "ABFM")
                        .param("effectiveDate", "2030-01-01")
                        .param("expirationDate", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/certification-form"));

        verify(certificationService, never()).addCertification(any(), any());
    }

    @Test
    void editLoadsTheExistingCertificationIntoTheForm() throws Exception {
        Certification certification = new Certification("ABFM", LocalDate.of(2020, 1, 1));
        when(certificationService.findByIdAndProviderId(5L, 1L)).thenReturn(certification);

        mockMvc.perform(get("/providers/1/certifications/5/edit").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/certification-form"))
                .andExpect(model().attribute("certificationId", 5L));
    }

    @Test
    void deleteRemovesAndRedirects() throws Exception {
        mockMvc.perform(post("/providers/1/certifications/5/delete")
                        .with(coordinator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(certificationService).delete(5L, 1L);
    }

    @Test
    void postsWithoutACsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/providers/1/certifications/5/delete").with(coordinator()))
                .andExpect(status().isForbidden());

        verify(certificationService, never()).delete(any(), any());
    }
}
