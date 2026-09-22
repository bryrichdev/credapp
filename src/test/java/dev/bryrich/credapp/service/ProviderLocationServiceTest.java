package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupLocation;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.ProviderLocation;
import dev.bryrich.credapp.entity.enums.PcpScp;
import dev.bryrich.credapp.exception.GroupLocationNotFoundException;
import dev.bryrich.credapp.exception.ProviderLocationNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotInGroupException;
import dev.bryrich.credapp.repository.GroupLocationRepository;
import dev.bryrich.credapp.repository.GroupProviderRepository;
import dev.bryrich.credapp.repository.ProviderLocationRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderLocationServiceTest {

    private ProviderLocationRepository providerLocationRepository;
    private GroupLocationRepository groupLocationRepository;
    private GroupProviderRepository groupProviderRepository;
    private ProviderRepository providerRepository;
    private ProviderLocationService service;

    private Group group;
    private GroupLocation location;
    private Provider provider;

    @BeforeEach
    void setUp() {
        providerLocationRepository = mock(ProviderLocationRepository.class);
        groupLocationRepository = mock(GroupLocationRepository.class);
        groupProviderRepository = mock(GroupProviderRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new ProviderLocationService(providerLocationRepository,
                groupLocationRepository, groupProviderRepository, providerRepository);

        group = new Group("Northside Health", "123456789");
        ReflectionTestUtils.setField(group, "id", 7L);
        location = new GroupLocation("Main Clinic", "123 Main St");
        location.setGroup(group);
        ReflectionTestUtils.setField(location, "id", 3L);
        provider = new Provider("Ada", "Byron");
        ReflectionTestUtils.setField(provider, "id", 1L);
    }

    @Test
    void refusesToPlaceAProviderAtALocationOfAGroupTheyAreNotIn() {
        when(groupLocationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(groupProviderRepository.existsByGroupIdAndProviderId(7L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(3L, 1L, PcpScp.PCP))
                .isInstanceOf(ProviderNotInGroupException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("7");

        verify(providerLocationRepository, never()).save(any());
    }

    @Test
    void placesAProviderWhoIsInTheLocationsGroup() {
        when(groupLocationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(groupProviderRepository.existsByGroupIdAndProviderId(7L, 1L)).thenReturn(true);
        when(providerLocationRepository.findByLocationIdAndProviderId(3L, 1L))
                .thenReturn(Optional.empty());
        when(providerLocationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ProviderLocation saved = service.assign(3L, 1L, PcpScp.PCP);

        assertThat(saved.getPcpScp()).isEqualTo(PcpScp.PCP);
        assertThat(saved.getGroupId()).isEqualTo(7L);
    }

    @Test
    void reassigningAnExistingPlacementUpdatesTheRoleInPlace() {
        ProviderLocation existing = new ProviderLocation(location, provider, PcpScp.PCP);
        when(groupLocationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(groupProviderRepository.existsByGroupIdAndProviderId(7L, 1L)).thenReturn(true);
        when(providerLocationRepository.findByLocationIdAndProviderId(3L, 1L))
                .thenReturn(Optional.of(existing));

        ProviderLocation result = service.assign(3L, 1L, PcpScp.SCP);

        assertThat(result).isSameAs(existing);
        assertThat(result.getPcpScp()).isEqualTo(PcpScp.SCP);
        verify(providerLocationRepository, never()).save(any());
    }

    @Test
    void assignThrowsWhenTheLocationDoesNotExist() {
        when(groupLocationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(404L, 1L, PcpScp.PCP))
                .isInstanceOf(GroupLocationNotFoundException.class);
    }

    @Test
    void assignThrowsWhenTheProviderDoesNotExist() {
        when(groupLocationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(3L, 99L, PcpScp.PCP))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void findOneThrowsWhenTheProviderIsNotAtThatLocation() {
        when(providerLocationRepository.findByLocationIdAndProviderId(3L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne(3L, 1L))
                .isInstanceOf(ProviderLocationNotFoundException.class);
    }

    @Test
    void unassignIsQuietWhenThereIsNothingToRemove() {
        when(providerLocationRepository.findByLocationIdAndProviderId(3L, 1L))
                .thenReturn(Optional.empty());

        service.unassign(3L, 1L);

        verify(providerLocationRepository, never()).delete(any());
    }
}
