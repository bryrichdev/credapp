package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.location.GroupLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The TEXT[] columns are mapped with @JdbcTypeCode(ARRAY); this is the round-trip proof. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProviderArrayColumnsTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProviderRepository providerRepository;

    @Test
    void arraysSurviveASaveAndReload() {
        Provider provider = new Provider("Ada", "Byron");
        provider.setLanguages(List.of("English", "Spanish", "ASL"));
        provider.setPrevNames(List.of("Ada Lovelace"));
        provider.setModalities(List.of("Telehealth"));
        provider.setAreasOfExpertise(List.of("Geriatrics", "Palliative care"));

        providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        Provider loaded = providerRepository.findById(provider.getId()).orElseThrow();

        assertThat(loaded.getLanguages()).containsExactly("English", "Spanish", "ASL");
        assertThat(loaded.getPrevNames()).containsExactly("Ada Lovelace");
        assertThat(loaded.getModalities()).containsExactly("Telehealth");
        assertThat(loaded.getAreasOfExpertise())
                .containsExactly("Geriatrics", "Palliative care");
    }

    @Test
    void anUnsetArrayComesBackEmptyRatherThanNull() {
        Provider provider = providerRepository.save(new Provider("Grace", "Hopper"));
        entityManager.flush();
        entityManager.clear();

        Provider loaded = providerRepository.findById(provider.getId()).orElseThrow();

        assertThat(loaded.getLanguages()).isNotNull().isEmpty();
        assertThat(loaded.getPrevNames()).isNotNull().isEmpty();
        assertThat(loaded.getModalities()).isNotNull().isEmpty();
        assertThat(loaded.getAreasOfExpertise()).isNotNull().isEmpty();
    }

    @Test
    void settingNullIsStoredAsAnEmptyArray() {
        Provider provider = new Provider("Alan", "Turing");
        provider.setLanguages(null);

        providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        assertThat(providerRepository.findById(provider.getId()).orElseThrow().getLanguages())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void groupLocationLanguagesAreAlsoAnArray() {
        Group group = entityManager.persistAndFlush(new Group("Northside Health", "123456789"));
        GroupLocation location = new GroupLocation("Main Clinic", "123 Main St");
        location.setGroup(group);
        location.setLanguages(List.of("English", "Spanish"));

        GroupLocation saved = entityManager.persistAndFlush(location);
        entityManager.clear();

        GroupLocation loaded = entityManager.find(GroupLocation.class, saved.getId());

        assertThat(loaded.getLanguages()).containsExactly("English", "Spanish");
    }
}
