package dev.bryrich.credapp.provider.certification;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationFormTest {

    private CertificationForm form() {
        CertificationForm form = new CertificationForm();
        form.setBoard("American Board of Family Medicine");
        form.setEffectiveDate(LocalDate.of(2020, 1, 1));
        return form;
    }

    @Test
    void treatsAMissingExpirationAsALifetimeCertification() {
        Certification certification = form().toEntity();

        assertThat(certification.getExpirationDate()).isNull();
        assertThat(certification.isLifetime()).isTrue();
    }

    @Test
    void allowsALifetimeCertificationThroughValidation() {
        assertThat(form().isExpirationAfterEffective()).isTrue();
    }

    @Test
    void rejectsAnExpirationOnOrBeforeTheEffectiveDate() {
        CertificationForm form = form();
        form.setExpirationDate(LocalDate.of(2019, 12, 31));
        assertThat(form.isExpirationAfterEffective()).isFalse();

        form.setExpirationDate(form.getEffectiveDate());
        assertThat(form.isExpirationAfterEffective()).isFalse();
    }

    @Test
    void acceptsAnExpirationAfterTheEffectiveDate() {
        CertificationForm form = form();
        form.setExpirationDate(LocalDate.of(2030, 1, 1));

        assertThat(form.isExpirationAfterEffective()).isTrue();
        assertThat(form.toEntity().isLifetime()).isFalse();
    }
}
