package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.LicenseStatus;
import dev.bryrich.credapp.exception.LicenseNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.LicenseRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
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

class LicenseServiceTest {

    private LicenseRepository licenseRepository;
    private ProviderRepository providerRepository;
    private LicenseService licenseService;

    @BeforeEach
    void setUp() {
        licenseRepository = mock(LicenseRepository.class);
        providerRepository = mock(ProviderRepository.class);
        licenseService = new LicenseService(licenseRepository, providerRepository);
    }

    private License license(String number) {
        return new License("UT", number, "MD", LocalDate.now().plusYears(1), LicenseStatus.ACTIVE);
    }

    @Test
    void findByProviderIdThrowsWhenTheProviderDoesNotExist() {
        when(providerRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> licenseService.findByProviderId(999L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(licenseRepository, never()).findByProviderId(anyLong());
    }

    @Test
    void findByProviderIdReturnsAnEmptyListWhenTheProviderHasNoLicenses() {
        when(providerRepository.existsById(1L)).thenReturn(true);
        when(licenseRepository.findByProviderId(1L)).thenReturn(List.of());

        assertThat(licenseService.findByProviderId(1L)).isEmpty();
    }

    @Test
    void findByProviderIdReturnsTheProvidersLicenses() {
        when(providerRepository.existsById(1L)).thenReturn(true);
        when(licenseRepository.findByProviderId(1L)).thenReturn(List.of(license("12345")));

        assertThat(licenseService.findByProviderId(1L))
                .singleElement()
                .satisfies(l -> assertThat(l.getLicenseNumber()).isEqualTo("12345"));
    }

    @Test
    void findExpiringSoonRejectsAWindowSmallerThanOneDay() {
        assertThatThrownBy(() -> licenseService.findExpiringSoon(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> licenseService.findExpiringSoon(-5))
                .isInstanceOf(IllegalArgumentException.class);

        verify(licenseRepository, never())
                .findExpiringWithProvider(any(), any(), any());
    }

    @Test
    void findExpiringSoonQueriesActiveLicensesInTheRequestedWindow() {
        when(licenseRepository.findExpiringWithProvider(any(), any(), any()))
                .thenReturn(List.of(license("SOON")));

        licenseService.findExpiringSoon(30);

        ArgumentCaptor<LicenseStatus> status = ArgumentCaptor.forClass(LicenseStatus.class);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(licenseRepository).findExpiringWithProvider(
                status.capture(), from.capture(), to.capture());

        assertThat(status.getValue()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(from.getValue()).isEqualTo(LocalDate.now());
        assertThat(to.getValue()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void findByIdAndProviderIdThrowsWhenTheLicenseBelongsToAnotherProvider() {
        when(licenseRepository.findByIdAndProviderId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.findByIdAndProviderId(42L, 1L))
                .isInstanceOf(LicenseNotFoundException.class);
    }
}
