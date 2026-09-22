package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
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

class ProviderServiceTest {

    private ProviderRepository providerRepository;
    private ProviderService service;

    @BeforeEach
    void setUp() {
        providerRepository = mock(ProviderRepository.class);
        service = new ProviderService(providerRepository);
    }

    @Test
    void findByIdThrowsWhenTheProviderIsMissing() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ProviderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findByIdWithLicensesUsesTheFetchingQuery() {
        Provider provider = new Provider("Ada", "Byron");
        when(providerRepository.findByIdWithLicenses(1L)).thenReturn(Optional.of(provider));

        assertThat(service.findByIdWithLicenses(1L)).isSameAs(provider);
        verify(providerRepository, never()).findById(any());
    }

    @Test
    void updateAppliesTheChangeToTheManagedEntity() {
        Provider provider = new Provider("Ada", "Byron");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        Provider result = service.update(1L, p -> p.setPhoneNumber("616-555-0100"));

        assertThat(result.getPhoneNumber()).isEqualTo("616-555-0100");
        // The entity is managed inside the transaction, so no explicit save is needed.
        verify(providerRepository, never()).save(any());
    }

    @Test
    void updateThrowsWhenTheProviderIsMissing() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, p -> p.setPhoneNumber("x")))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenTheProviderIsMissing() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(providerRepository, never()).delete(any());
    }
}
