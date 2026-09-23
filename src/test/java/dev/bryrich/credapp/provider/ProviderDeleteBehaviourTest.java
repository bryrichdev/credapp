package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.provider.certification.Certification;
import dev.bryrich.credapp.provider.certification.CertificationRepository;
import dev.bryrich.credapp.provider.disclosure.ChargeClassification;
import dev.bryrich.credapp.provider.disclosure.ChargeStatus;
import dev.bryrich.credapp.provider.disclosure.CriminalCharge;
import dev.bryrich.credapp.provider.disclosure.CriminalChargeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ON DELETE choices are deliberate: records a payer may later audit hold a provider in
 * place, ordinary supporting records go with them.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProviderDeleteBehaviourTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private CriminalChargeRepository chargeRepository;

    private Provider newProvider() {
        return providerRepository.saveAndFlush(new Provider("Ada", "Byron"));
    }

    @Test
    void deletingAProviderTakesTheirCertificationsWithThem() {
        Provider provider = newProvider();
        Certification certification = new Certification("ABFM", LocalDate.of(2020, 1, 1));
        certification.setProvider(provider);
        certificationRepository.saveAndFlush(certification);
        Long providerId = provider.getId();
        Long certificationId = certification.getId();

        // Start deletion with no managed children referencing the provider. Otherwise
        // Hibernate rejects the in-memory relationship before Postgres sees the delete.
        entityManager.clear();
        providerRepository.deleteById(providerId);
        providerRepository.flush();
        // The cascade happens in Postgres, not in Hibernate, so the row has to be
        // re-read rather than trusted from the first-level cache.
        entityManager.clear();

        assertThat(providerRepository.findById(providerId)).isEmpty();
        assertThat(certificationRepository.findById(certificationId)).isEmpty();
    }

    @Test
    void aDisclosedChargeStopsTheProviderFromBeingDeleted() {
        Provider provider = newProvider();
        CriminalCharge charge = new CriminalCharge(
                ChargeClassification.MISDEMEANOR, ChargeStatus.CONVICTED);
        charge.setProvider(provider);
        chargeRepository.saveAndFlush(charge);
        Long providerId = provider.getId();

        // Let the database foreign key, rather than a managed child, block deletion.
        entityManager.clear();

        assertThatThrownBy(() -> {
            providerRepository.deleteById(providerId);
            providerRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
