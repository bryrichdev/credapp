package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.ProviderTaxonomy;
import dev.bryrich.credapp.entity.Taxonomy;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.exception.ProviderTaxonomyNotFoundException;
import dev.bryrich.credapp.exception.TaxonomyNotFoundException;
import dev.bryrich.credapp.repository.ProviderRepository;
import dev.bryrich.credapp.repository.ProviderTaxonomyRepository;
import dev.bryrich.credapp.repository.TaxonomyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderTaxonomyServiceTest {

    private ProviderTaxonomyRepository providerTaxonomyRepository;
    private ProviderRepository providerRepository;
    private TaxonomyRepository taxonomyRepository;
    private ProviderTaxonomyService service;

    private final Provider provider = new Provider("Ada", "Byron");
    private final Taxonomy familyMedicine = new Taxonomy("207Q00000X", "Family Medicine", null);

    @BeforeEach
    void setUp() {
        providerTaxonomyRepository = mock(ProviderTaxonomyRepository.class);
        providerRepository = mock(ProviderRepository.class);
        taxonomyRepository = mock(TaxonomyRepository.class);
        service = new ProviderTaxonomyService(
                providerTaxonomyRepository, providerRepository, taxonomyRepository);
    }

    @Test
    void findByProviderIdThrowsWhenTheProviderDoesNotExist() {
        when(providerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByProviderId(99L))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(providerTaxonomyRepository, never()).findByProviderIdWithTaxonomy(anyLong());
    }

    @Test
    void assignRejectsATaxonomyCodeThatIsNotSeeded() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(taxonomyRepository.findById("999X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(1L, "999X", false))
                .isInstanceOf(TaxonomyNotFoundException.class);

        verify(providerTaxonomyRepository, never()).save(any());
    }

    @Test
    void assigningAPrimaryClearsTheExistingPrimaryFirst() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(taxonomyRepository.findById("207Q00000X")).thenReturn(Optional.of(familyMedicine));
        when(providerTaxonomyRepository.findByProviderId(1L)).thenReturn(List.of());
        when(providerTaxonomyRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.assign(1L, "207Q00000X", true);

        // A partial unique index allows one primary row, so the clear has to land first.
        var order = inOrder(providerTaxonomyRepository);
        order.verify(providerTaxonomyRepository).clearPrimaryForProvider(1L);
        order.verify(providerTaxonomyRepository).save(any());
    }

    @Test
    void assigningANonPrimaryLeavesTheExistingPrimaryAlone() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(taxonomyRepository.findById("207Q00000X")).thenReturn(Optional.of(familyMedicine));
        when(providerTaxonomyRepository.findByProviderId(1L)).thenReturn(List.of());
        when(providerTaxonomyRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.assign(1L, "207Q00000X", false);

        verify(providerTaxonomyRepository, never()).clearPrimaryForProvider(anyLong());
    }

    @Test
    void assigningACodeTheProviderAlreadyHasUpdatesTheFlagRatherThanInserting() {
        ProviderTaxonomy existing = new ProviderTaxonomy(provider, familyMedicine, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(taxonomyRepository.findById("207Q00000X")).thenReturn(Optional.of(familyMedicine));
        when(providerTaxonomyRepository.findByProviderId(1L)).thenReturn(List.of(existing));

        ProviderTaxonomy result = service.assign(1L, "207Q00000X", true);

        assertThat(result).isSameAs(existing);
        assertThat(result.isPrimary()).isTrue();
        verify(providerTaxonomyRepository, never()).save(any());
    }

    @Test
    void unassignThrowsWhenTheProviderDoesNotHaveThatCode() {
        when(providerTaxonomyRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(1L, "207Q00000X"))
                .isInstanceOf(ProviderTaxonomyNotFoundException.class);

        verify(providerTaxonomyRepository, never()).delete(any());
    }

    @Test
    void findPrimaryReturnsNullWhenNoneIsMarked() {
        when(providerTaxonomyRepository.findByProviderIdAndPrimaryTrue(1L))
                .thenReturn(Optional.empty());

        assertThat(service.findPrimary(1L)).isNull();
    }
}
