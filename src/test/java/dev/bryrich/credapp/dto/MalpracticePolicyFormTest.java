package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.enums.CoverageScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These mirror the three CHECK constraints on malpractice_policies. If one of these
 * assertions changes, the migration has to change with it.
 */
class MalpracticePolicyFormTest {

    private MalpracticePolicyForm form;

    @BeforeEach
    void setUp() {
        form = new MalpracticePolicyForm();
        form.setPolicyNumber("P-1");
        form.setCarrierName("Acme Mutual");
        form.setTypeOfCoverage("Claims-made");
        form.setEffectiveDate(LocalDate.of(2026, 1, 1));
        form.setSharedIndividual(CoverageScope.INDIVIDUAL);
    }

    @Test
    void acceptsAPolicyOwnedByOneProvider() {
        form.setProviderId(1L);

        assertThat(form.isOwnerExclusive()).isTrue();
    }

    @Test
    void acceptsAPolicyOwnedByOneGroup() {
        form.setGroupId(1L);

        assertThat(form.isOwnerExclusive()).isTrue();
    }

    @Test
    void rejectsAPolicyNamingBothAProviderAndAGroup() {
        form.setProviderId(1L);
        form.setGroupId(2L);

        assertThat(form.isOwnerExclusive()).isFalse();
    }

    @Test
    void rejectsAPolicyNamingNeither() {
        assertThat(form.isOwnerExclusive()).isFalse();
    }

    @Test
    void allowsAnOpenEndedPolicyWithNoExpiration() {
        assertThat(form.isExpirationAfterEffective()).isTrue();
    }

    @Test
    void rejectsAnExpirationOnOrBeforeTheEffectiveDate() {
        form.setExpirationDate(LocalDate.of(2025, 12, 31));
        assertThat(form.isExpirationAfterEffective()).isFalse();

        form.setExpirationDate(form.getEffectiveDate());
        assertThat(form.isExpirationAfterEffective()).isFalse();
    }

    @Test
    void acceptsAnExpirationAfterTheEffectiveDate() {
        form.setExpirationDate(LocalDate.of(2027, 1, 1));

        assertThat(form.isExpirationAfterEffective()).isTrue();
    }

    @Test
    void allowsAnOriginalEffectiveDateOnOrBeforeTheCurrentTerm() {
        form.setOriginalEffectiveDate(LocalDate.of(2020, 6, 1));
        assertThat(form.isOriginalOnOrBeforeEffective()).isTrue();

        form.setOriginalEffectiveDate(form.getEffectiveDate());
        assertThat(form.isOriginalOnOrBeforeEffective()).isTrue();
    }

    @Test
    void rejectsAnOriginalEffectiveDateAfterTheCurrentTerm() {
        form.setOriginalEffectiveDate(LocalDate.of(2026, 6, 1));

        assertThat(form.isOriginalOnOrBeforeEffective()).isFalse();
    }
}
