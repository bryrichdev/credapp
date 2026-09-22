package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.HospitalPrivilege;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.PrivilegeStatus;
import dev.bryrich.credapp.exception.HospitalPrivilegeNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.HospitalPrivilegeRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HospitalPrivilegeServiceTest {

    private HospitalPrivilegeRepository privilegeRepository;
    private ProviderRepository providerRepository;
    private HospitalPrivilegeService service;

    private final Provider provider = new Provider("Ada", "Byron");
    private final Provider colleague = new Provider("Grace", "Hopper");

    @BeforeEach
    void setUp() {
        privilegeRepository = mock(HospitalPrivilegeRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new HospitalPrivilegeService(privilegeRepository, providerRepository);
        when(privilegeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private HospitalPrivilege privilege() {
        return new HospitalPrivilege("Mercy Health", PrivilegeStatus.ACTIVE);
    }

    @Test
    void refusesToListAProviderAsTheirOwnAdmittingPhysician() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service.addPrivilege(1L, privilege(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own admitting physician");

        verify(privilegeRepository, never()).save(any());
    }

    @Test
    void acceptsAColleagueAsTheAdmittingPhysician() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.findById(2L)).thenReturn(Optional.of(colleague));

        HospitalPrivilege saved = service.addPrivilege(1L, privilege(), 2L);

        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getAdmittingPhysician()).isSameAs(colleague);
    }

    @Test
    void leavesTheAdmittingPhysicianUnsetWhenNoneIsNamed() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        HospitalPrivilege saved = service.addPrivilege(1L, privilege(), null);

        assertThat(saved.getAdmittingPhysician()).isNull();
    }

    @Test
    void throwsWhenTheNamedColleagueDoesNotExist() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPrivilege(1L, privilege(), 404L))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void updateThrowsWhenThePrivilegeBelongsToAnotherProvider() {
        when(privilegeRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, 1L, null, p -> { }))
                .isInstanceOf(HospitalPrivilegeNotFoundException.class);
    }

    @Test
    void updateClearsTheAdmittingPhysicianWhenNoneIsNamed() {
        HospitalPrivilege existing = privilege();
        existing.setAdmittingPhysician(colleague);
        when(privilegeRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.of(existing));

        HospitalPrivilege result = service.update(5L, 1L, null, p -> p.setStatus(PrivilegeStatus.COURTESY));

        assertThat(result.getAdmittingPhysician()).isNull();
        assertThat(result.getStatus()).isEqualTo(PrivilegeStatus.COURTESY);
    }
}
