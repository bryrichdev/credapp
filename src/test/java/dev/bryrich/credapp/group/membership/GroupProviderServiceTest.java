package dev.bryrich.credapp.group.membership;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupNotFoundException;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupProviderServiceTest {

    private GroupProviderRepository groupProviderRepository;
    private GroupRepository groupRepository;
    private ProviderRepository providerRepository;
    private GroupProviderService service;

    private final Group group = new Group("Northside Health", "123456789");
    private final Provider provider = new Provider("Ada", "Byron");

    @BeforeEach
    void setUp() {
        groupProviderRepository = mock(GroupProviderRepository.class);
        groupRepository = mock(GroupRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new GroupProviderService(groupProviderRepository, groupRepository, providerRepository);
    }

    @Test
    void assignCreatesTheMembershipWhenThereIsNone() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(groupProviderRepository.findByGroupIdAndProviderId(7L, 1L)).thenReturn(Optional.empty());
        when(groupProviderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        GroupProvider saved = service.assign(7L, 1L, LocalDate.of(2026, 1, 1));

        assertThat(saved.getGroup()).isSameAs(group);
        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void assigningTwiceUpdatesTheDateRatherThanDuplicating() {
        GroupProvider existing = new GroupProvider(group, provider, LocalDate.of(2025, 1, 1));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(groupProviderRepository.findByGroupIdAndProviderId(7L, 1L))
                .thenReturn(Optional.of(existing));

        GroupProvider result = service.assign(7L, 1L, LocalDate.of(2026, 6, 1));

        assertThat(result).isSameAs(existing);
        assertThat(result.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        verify(groupProviderRepository, never()).save(any());
    }

    @Test
    void assignThrowsWhenTheGroupIsMissing() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(99L, 1L, null))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void assignThrowsWhenTheProviderIsMissing() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(7L, 99L, null))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void unassignIsQuietWhenThereIsNoMembership() {
        when(groupProviderRepository.findByGroupIdAndProviderId(7L, 1L)).thenReturn(Optional.empty());

        service.unassign(7L, 1L);

        verify(groupProviderRepository, never()).delete(any());
    }
}
