package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class MalpracticePolicyConstraintsTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MalpracticePolicyRepository policyRepository;

    private Provider provider;
    private Group group;

    @BeforeEach
    void setUp() {
        provider = entityManager.persistAndFlush(new Provider("Ada", "Byron"));
        group = entityManager.persistAndFlush(new Group("Northside Health", "123456789"));
    }

    private MalpracticePolicy forProvider(String number) {
        return MalpracticePolicy.forProvider(provider, number, "Acme Mutual", "Claims-made",
                LocalDate.of(2026, 1, 1), CoverageScope.INDIVIDUAL);
    }

    @Test
    void aPolicyOwnedByOneProviderSaves() {
        policyRepository.save(forProvider("P-1"));
        entityManager.flush();

        assertThat(policyRepository.findByProviderIdOrderByEffectiveDateDesc(provider.getId()))
                .hasSize(1);
    }

    @Test
    void aPolicyOwnedByOneGroupSaves() {
        policyRepository.save(MalpracticePolicy.forGroup(group, "P-2", "Acme Mutual",
                "Occurrence", LocalDate.of(2026, 1, 1), CoverageScope.SHARED));
        entityManager.flush();

        assertThat(policyRepository.findByGroupIdOrderByEffectiveDateDesc(group.getId()))
                .hasSize(1);
    }

    @Test
    void aPolicyNamingBothAProviderAndAGroupIsRejected() {
        MalpracticePolicy policy = forProvider("P-3");
        ReflectionTestUtils.setField(policy, "group", group);
        policyRepository.save(policy);

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPolicyNamingNeitherIsRejected() {
        MalpracticePolicy policy = forProvider("P-4");
        ReflectionTestUtils.setField(policy, "provider", null);
        policyRepository.save(policy);

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anExpirationBeforeTheEffectiveDateIsRejected() {
        MalpracticePolicy policy = forProvider("P-5");
        policy.setExpirationDate(LocalDate.of(2025, 1, 1));
        policyRepository.save(policy);

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameCarrierCannotIssueTheSamePolicyNumberTwice() {
        policyRepository.save(forProvider("P-6"));
        entityManager.flush();

        policyRepository.save(forProvider("P-6"));

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoCarriersMayUseTheSamePolicyNumber() {
        policyRepository.save(forProvider("SHARED-NUMBER"));
        policyRepository.save(MalpracticePolicy.forProvider(provider, "SHARED-NUMBER",
                "Second Carrier", "Occurrence", LocalDate.of(2026, 1, 1),
                CoverageScope.INDIVIDUAL));

        entityManager.flush();

        assertThat(policyRepository.findByProviderIdOrderByEffectiveDateDesc(provider.getId()))
                .hasSize(2);
    }
}
