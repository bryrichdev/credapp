package dev.bryrich.credapp.provider.certification;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class CertificationServiceTest {

    private CertificationRepository certificationRepository;
    private ProviderRepository providerRepository;
    private CertificationService service;

    private final Provider provider = new Provider("Ada", "Byron");

    @BeforeEach
    void setUp() {
        certificationRepository = mock(CertificationRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new CertificationService(certificationRepository, providerRepository);
    }

    @Test
    void findByProviderIdThrowsWhenTheProviderDoesNotExist() {
        when(providerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByProviderId(99L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(certificationRepository, never())
                .findByProviderIdOrderByEffectiveDateDesc(anyLong());
    }

    @Test
    void addCertificationAttachesTheProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(certificationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Certification saved = service.addCertification(1L,
                new Certification("ABFM", LocalDate.of(2020, 1, 1)));

        assertThat(saved.getProvider()).isSameAs(provider);
    }

    @Test
    void findExpiringSoonRejectsAWindowSmallerThanOneDay() {
        assertThatThrownBy(() -> service.findExpiringSoon(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.findExpiringSoon(-1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(certificationRepository, never()).findExpiringBetween(any(), any());
    }

    @Test
    void findExpiringSoonQueriesFromTodayToTheEndOfTheWindow() {
        when(certificationRepository.findExpiringBetween(any(), any())).thenReturn(List.of());

        service.findExpiringSoon(60);

        verify(certificationRepository)
                .findExpiringBetween(LocalDate.now(), LocalDate.now().plusDays(60));
    }

    @Test
    void findByIdAndProviderIdThrowsWhenItBelongsToAnotherProvider() {
        when(certificationRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdAndProviderId(5L, 1L))
                .isInstanceOf(CertificationNotFoundException.class);
    }
}
