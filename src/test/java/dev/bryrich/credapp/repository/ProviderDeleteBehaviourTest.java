package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.entity.Certification;
import dev.bryrich.credapp.entity.CriminalCharge;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.ChargeClassification;
import dev.bryrich.credapp.entity.enums.ChargeStatus;
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
        return entityManager.persistAndFlush(new Provider("Ada", "Byron"));
    }

    @Test
    void deletingAProviderTakesTheirCertificationsWithThem() {
        Provider provider = newProvider();
        Certification certification = new Certification("ABFM", LocalDate.of(2020, 1, 1));
        certification.setProvider(provider);
        certificationRepository.save(certification);
        entityManager.flush();
        Long certificationId = certification.getId();

        providerRepository.delete(provider);
        entityManager.flush();
        entityManager.clear();

        assertThat(certificationRepository.findById(certificationId)).isEmpty();
    }

    @Test
    void aDisclosedChargeStopsTheProviderFromBeingDeleted() {
        Provider provider = newProvider();
        CriminalCharge charge = new CriminalCharge(
                ChargeClassification.MISDEMEANOR, ChargeStatus.CONVICTED);
        charge.setProvider(provider);
        chargeRepository.save(charge);
        entityManager.flush();

        providerRepository.delete(provider);

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
