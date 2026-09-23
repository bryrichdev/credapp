package dev.bryrich.credapp.taxonomy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxonomyServiceTest {

    private TaxonomyRepository taxonomyRepository;
    private TaxonomyService service;

    private final Taxonomy familyMedicine = new Taxonomy("207Q00000X", "Family Medicine",
            "Allopathic & Osteopathic Physicians");

    @BeforeEach
    void setUp() {
        taxonomyRepository = mock(TaxonomyRepository.class);
        service = new TaxonomyService(taxonomyRepository);
    }

    @Test
    void findByCodeThrowsWhenTheCodeIsNotSeeded() {
        when(taxonomyRepository.findById("999X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("999X"))
                .isInstanceOf(TaxonomyNotFoundException.class)
                .hasMessageContaining("999X");
    }

    @Test
    void searchFallsBackToTheFullListWhenNoTermIsGiven() {
        when(taxonomyRepository.findAllByOrderBySpecialtyAsc()).thenReturn(List.of(familyMedicine));

        assertThat(service.search(null)).containsExactly(familyMedicine);
        assertThat(service.search("   ")).containsExactly(familyMedicine);

        verify(taxonomyRepository, never())
                .findBySpecialtyContainingIgnoreCaseOrderBySpecialtyAsc(anyString());
    }

    @Test
    void searchDelegatesWhenATermIsGiven() {
        when(taxonomyRepository.findBySpecialtyContainingIgnoreCaseOrderBySpecialtyAsc("family"))
                .thenReturn(List.of(familyMedicine));

        assertThat(service.search("family")).containsExactly(familyMedicine);
    }

    @Test
    void labelPairsTheCodeWithTheSpecialty() {
        assertThat(familyMedicine.getLabel()).isEqualTo("207Q00000X — Family Medicine");
    }
}
