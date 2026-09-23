package dev.bryrich.credapp.taxonomy;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.provider.Provider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one-primary-per-provider rule lives in a partial unique index, not in application
 * code. These prove the index is actually there.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TaxonomyConstraintsTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaxonomyRepository taxonomyRepository;

    @Autowired
    private ProviderTaxonomyRepository providerTaxonomyRepository;

    private Provider newProvider(String first, String last) {
        Provider provider = new Provider(first, last);
        return entityManager.persistAndFlush(provider);
    }

    private Taxonomy code(String code, String specialty) {
        return taxonomyRepository.findById(code).orElseGet(
                () -> entityManager.persistAndFlush(new Taxonomy(code, specialty, "Test Grouping")));
    }

    @Test
    void theSeedMigrationLoadsTheStarterCodeSet() {
        assertThat(taxonomyRepository.count()).isPositive();
        assertThat(taxonomyRepository.findById("207Q00000X"))
                .isPresent()
                .get()
                .satisfies(t -> assertThat(t.getSpecialty()).isEqualTo("Family Medicine"));
    }

    @Test
    void everySeededCodeIsTenCharacters() {
        assertThat(taxonomyRepository.findAll())
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t.getCode()).hasSize(10));
    }

    @Test
    void aProviderCanHoldSeveralNonPrimarySpecialties() {
        Provider provider = newProvider("Ada", "Byron");
        providerTaxonomyRepository.save(
                new ProviderTaxonomy(provider, code("207Q00000X", "Family Medicine"), false));
        providerTaxonomyRepository.save(
                new ProviderTaxonomy(provider, code("207R00000X", "Internal Medicine"), false));

        entityManager.flush();

        assertThat(providerTaxonomyRepository.findByProviderId(provider.getId())).hasSize(2);
    }

    @Test
    void aSecondPrimarySpecialtyForTheSameProviderIsRejected() {
        Provider provider = newProvider("Grace", "Hopper");
        providerTaxonomyRepository.saveAndFlush(
                new ProviderTaxonomy(provider, code("207Q00000X", "Family Medicine"), true));

        ProviderTaxonomy second =
                new ProviderTaxonomy(provider, code("207R00000X", "Internal Medicine"), true);

        // saveAndFlush, not entityManager.flush(): the write has to happen inside the
        // assertion, and going through the repository is what translates the driver error
        // into Spring's DataIntegrityViolationException.
        assertThatThrownBy(() -> providerTaxonomyRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoDifferentProvidersMayEachHaveAPrimary() {
        Provider first = newProvider("Ada", "Byron");
        Provider second = newProvider("Grace", "Hopper");
        Taxonomy familyMedicine = code("207Q00000X", "Family Medicine");

        providerTaxonomyRepository.save(new ProviderTaxonomy(first, familyMedicine, true));
        providerTaxonomyRepository.save(new ProviderTaxonomy(second, familyMedicine, true));

        entityManager.flush();

        assertThat(providerTaxonomyRepository.findByProviderIdAndPrimaryTrue(first.getId())).isPresent();
        assertThat(providerTaxonomyRepository.findByProviderIdAndPrimaryTrue(second.getId())).isPresent();
    }
}
