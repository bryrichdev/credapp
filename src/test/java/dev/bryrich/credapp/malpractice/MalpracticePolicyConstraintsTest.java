package dev.bryrich.credapp.malpractice;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.provider.Provider;
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
        policyRepository.saveAndFlush(forProvider("P-1"));

        assertThat(policyRepository.findByProviderIdOrderByEffectiveDateDesc(provider.getId()))
                .hasSize(1);
    }

    @Test
    void aPolicyOwnedByOneGroupSaves() {
        policyRepository.saveAndFlush(MalpracticePolicy.forGroup(group, "P-2", "Acme Mutual",
                "Occurrence", LocalDate.of(2026, 1, 1), CoverageScope.SHARED));

        assertThat(policyRepository.findByGroupIdOrderByEffectiveDateDesc(group.getId()))
                .hasSize(1);
    }

    @Test
    void aPolicyNamingBothAProviderAndAGroupIsRejected() {
        MalpracticePolicy policy = forProvider("P-3");
        ReflectionTestUtils.setField(policy, "group", group);

        assertThatThrownBy(() -> policyRepository.saveAndFlush(policy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPolicyNamingNeitherIsRejected() {
        MalpracticePolicy policy = forProvider("P-4");
        ReflectionTestUtils.setField(policy, "provider", null);

        assertThatThrownBy(() -> policyRepository.saveAndFlush(policy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anExpirationBeforeTheEffectiveDateIsRejected() {
        MalpracticePolicy policy = forProvider("P-5");
        policy.setExpirationDate(LocalDate.of(2025, 1, 1));

        assertThatThrownBy(() -> policyRepository.saveAndFlush(policy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameCarrierCannotIssueTheSamePolicyNumberTwice() {
        policyRepository.saveAndFlush(forProvider("P-6"));

        MalpracticePolicy duplicate = forProvider("P-6");

        assertThatThrownBy(() -> policyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoCarriersMayUseTheSamePolicyNumber() {
        policyRepository.saveAndFlush(forProvider("SHARED-NUMBER"));
        policyRepository.saveAndFlush(MalpracticePolicy.forProvider(provider, "SHARED-NUMBER",
                "Second Carrier", "Occurrence", LocalDate.of(2026, 1, 1),
                CoverageScope.INDIVIDUAL));

        assertThat(policyRepository.findByProviderIdOrderByEffectiveDateDesc(provider.getId()))
                .hasSize(2);
    }
}
