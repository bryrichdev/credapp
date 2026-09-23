package dev.bryrich.credapp.provider.disclosure;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CriminalChargeServiceTest {

    private CriminalChargeRepository chargeRepository;
    private ProviderRepository providerRepository;
    private CriminalChargeService service;

    private final Provider provider = new Provider("Ada", "Byron");

    @BeforeEach
    void setUp() {
        chargeRepository = mock(CriminalChargeRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new CriminalChargeService(chargeRepository, providerRepository);
    }

    private CriminalCharge charge() {
        return new CriminalCharge(ChargeClassification.MISDEMEANOR, ChargeStatus.DISMISSED);
    }

    @Test
    void findByProviderIdThrowsWhenTheProviderDoesNotExist() {
        when(providerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByProviderId(99L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(chargeRepository, never()).findByProviderIdOrderByIncidentDateDesc(anyLong());
    }

    @Test
    void addChargeAttachesTheProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(chargeRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.addCharge(1L, charge()).getProvider()).isSameAs(provider);
    }

    @Test
    void filtersByStatusWithoutCheckingTheProviderTwice() {
        when(chargeRepository.findByProviderIdAndStatus(1L, ChargeStatus.PENDING))
                .thenReturn(List.of(charge()));

        assertThat(service.findByProviderIdAndStatus(1L, ChargeStatus.PENDING)).hasSize(1);
    }

    @Test
    void deleteThrowsWhenTheChargeBelongsToAnotherProvider() {
        when(chargeRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L, 1L))
                .isInstanceOf(CriminalChargeNotFoundException.class);

        verify(chargeRepository, never()).delete(any());
    }
}
