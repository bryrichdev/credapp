package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.LicenseStatus;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.Sex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProviderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private LicenseRepository licenseRepository;

    private Provider newProvider() {
        Provider provider = new Provider("Alice", "Nguyen");
        provider.setNpi("1234567890");
        provider.setDob(LocalDate.of(1985, 4, 12));
        provider.setSex(Sex.FEMALE);
        return provider;
    }

    private License newLicense(String state, String number, LocalDate expiration) {
        return new License(state, number, "MD", expiration, LicenseStatus.ACTIVE);
    }

    @Test
    void savesProviderWithLicenseAndReadsItBack() {
        Provider provider = newProvider();
        provider.addLicense(newLicense("NC", "A12345", LocalDate.now().plusMonths(6)));

        providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        Provider loaded = providerRepository.findByIdWithLicenses(provider.getId())
                .orElseThrow();

        assertThat(loaded.getFirstName()).isEqualTo("Alice");
        assertThat(loaded.getNpi()).isEqualTo("1234567890");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();

        assertThat(loaded.getLicenses()).hasSize(1);
        License license = loaded.getLicenses().get(0);
        assertThat(license.getId()).isNotNull();
        assertThat(license.getLicenseNumber()).isEqualTo("A12345");
        assertThat(license.getProvider().getId()).isEqualTo(loaded.getId());
    }

    @Test
    void removingALicenseDeletesTheRow() {
        Provider provider = newProvider();
        provider.addLicense(newLicense("NC", "A12345", LocalDate.now().plusMonths(6)));
        providerRepository.save(provider);
        entityManager.flush();

        Long licenseId = provider.getLicenses().get(0).getId();

        provider.removeLicense(provider.getLicenses().get(0));
        providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        assertThat(licenseRepository.findById(licenseId)).isEmpty();
    }

    @Test
    void findsLicensesExpiringInAWindow() {
        Provider provider = newProvider();
        provider.addLicense(newLicense("NC", "SOON", LocalDate.now().plusDays(20)));
        provider.addLicense(newLicense("SC", "LATER", LocalDate.now().plusYears(2)));
        providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        List<License> expiring = licenseRepository.findExpiringWithProvider(
                LicenseStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30));

        assertThat(expiring)
                .hasSize(1)
                .allSatisfy(l -> assertThat(l.getLicenseNumber()).isEqualTo("SOON"));
    }
}