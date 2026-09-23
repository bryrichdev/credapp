package dev.bryrich.credapp.malpractice;

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

class MalpracticeClaimServiceTest {

    private MalpracticeClaimRepository claimRepository;
    private MalpracticePolicyRepository policyRepository;
    private ProviderRepository providerRepository;
    private MalpracticeClaimService service;

    private final Provider provider = new Provider("Ada", "Byron");

    @BeforeEach
    void setUp() {
        claimRepository = mock(MalpracticeClaimRepository.class);
        policyRepository = mock(MalpracticePolicyRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new MalpracticeClaimService(claimRepository, policyRepository, providerRepository);
        when(claimRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private MalpracticeClaim claim() {
        return new MalpracticeClaim("C-1", "Acme Mutual");
    }

    @Test
    void savesAClaimWithNoPolicyLink() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        MalpracticeClaim saved = service.addClaim(1L, claim(), null);

        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getPolicy()).isNull();
        verify(policyRepository, never()).findById(any());
    }

    @Test
    void linksTheClaimToAPolicyWhenOneIsNamed() {
        MalpracticePolicy policy = MalpracticePolicy.forProvider(provider, "P-1",
                "Acme Mutual", "Claims-made", LocalDate.of(2026, 1, 1), CoverageScope.INDIVIDUAL);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(policyRepository.findById(4L)).thenReturn(Optional.of(policy));

        MalpracticeClaim saved = service.addClaim(1L, claim(), 4L);

        assertThat(saved.getPolicy()).isSameAs(policy);
    }

    @Test
    void throwsWhenTheNamedPolicyDoesNotExist() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(policyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addClaim(1L, claim(), 404L))
                .isInstanceOf(MalpracticePolicyNotFoundException.class);

        verify(claimRepository, never()).save(any());
    }

    @Test
    void throwsWhenTheProviderDoesNotExist() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addClaim(99L, claim(), null))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenTheClaimBelongsToAnotherProvider() {
        when(claimRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L, 1L))
                .isInstanceOf(MalpracticeClaimNotFoundException.class);

        verify(claimRepository, never()).delete(any());
    }
}
