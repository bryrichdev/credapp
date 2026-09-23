package dev.bryrich.credapp.provider.reference;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderReferenceServiceTest {

    private ProviderReferenceRepository referenceRepository;
    private ProviderRepository providerRepository;
    private ProviderReferenceService service;

    private final Provider provider = new Provider("Ada", "Byron");

    @BeforeEach
    void setUp() {
        referenceRepository = mock(ProviderReferenceRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new ProviderReferenceService(referenceRepository, providerRepository);
    }

    @Test
    void findByProviderIdThrowsWhenTheProviderDoesNotExist() {
        when(providerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByProviderId(99L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(referenceRepository, never()).findByProviderIdOrderByName(anyLong());
    }

    @Test
    void addReferenceAttachesTheProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(referenceRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ProviderReference saved = service.addReference(1L,
                new ProviderReference("Dr Grace Hopper", "Colleague"));

        assertThat(saved.getProvider()).isSameAs(provider);
    }

    @Test
    void updateThrowsWhenTheReferenceBelongsToAnotherProvider() {
        when(referenceRepository.findByIdAndProviderId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, 1L, r -> { }))
                .isInstanceOf(ProviderReferenceNotFoundException.class);
    }
}
