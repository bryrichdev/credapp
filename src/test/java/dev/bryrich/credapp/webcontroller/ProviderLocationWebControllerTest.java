package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupLocation;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.PcpScp;
import dev.bryrich.credapp.exception.ProviderNotInGroupException;
import dev.bryrich.credapp.service.ProviderLocationService;
import dev.bryrich.credapp.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static dev.bryrich.credapp.webcontroller.WebTestSupport.coordinator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(ProviderLocationWebController.class)
class ProviderLocationWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderLocationService providerLocationService;

    @MockitoBean
    private ProviderService providerService;

    private final Provider provider = WebTestSupport.provider(1L, "Ada", "Byron");

    private GroupLocation location() {
        Group group = new Group("Northside Health", "123456789");
        ReflectionTestUtils.setField(group, "id", 7L);
        GroupLocation location = new GroupLocation("Main Clinic", "123 Main St");
        location.setGroup(group);
        ReflectionTestUtils.setField(location, "id", 3L);
        return location;
    }

    @BeforeEach
    void setUp() {
        when(providerService.findById(1L)).thenReturn(provider);
        when(providerLocationService.findAssignableLocations(1L)).thenReturn(List.of(location()));
    }

    @Test
    void newLocationOffersOnlyAssignableLocations() throws Exception {
        mockMvc.perform(get("/providers/1/locations/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/location-form"))
                .andExpect(model().attributeExists("assignableLocations", "pcpScpOptions"));
    }

    @Test
    void theFormStillRendersWhenTheProviderIsInNoGroup() throws Exception {
        when(providerLocationService.findAssignableLocations(1L)).thenReturn(List.of());

        mockMvc.perform(get("/providers/1/locations/new").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/location-form"));
    }

    @Test
    void createAssignsTheLocationAndRedirects() throws Exception {
        mockMvc.perform(post("/providers/1/locations")
                        .with(coordinator())
                        .with(csrf())
                        .param("locationId", "3")
                        .param("pcpScp", "PCP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/providers/1"));

        verify(providerLocationService).assign(3L, 1L, PcpScp.PCP);
    }

    @Test
    void createRedisplaysTheFormWhenNoLocationIsChosen() throws Exception {
        mockMvc.perform(post("/providers/1/locations")
                        .with(coordinator())
                        .with(csrf())
                        .param("pcpScp", "PCP"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/location-form"))
                .andExpect(model().attributeHasFieldErrors("form", "locationId"));

        verify(providerLocationService, never()).assign(any(), any(), any());
    }

    @Test
    void aProviderOutsideTheGroupSurfacesAsAConflict() throws Exception {
        doThrow(new ProviderNotInGroupException(1L, 7L))
                .when(providerLocationService).assign(3L, 1L, PcpScp.PCP);

        mockMvc.perform(post("/providers/1/locations")
                        .with(coordinator())
                        .with(csrf())
                        .param("locationId", "3")
                        .param("pcpScp", "PCP"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteUnassignsTheLocation() throws Exception {
        mockMvc.perform(post("/providers/1/locations/3/delete")
                        .with(coordinator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(providerLocationService).unassign(3L, 1L);
    }
}
