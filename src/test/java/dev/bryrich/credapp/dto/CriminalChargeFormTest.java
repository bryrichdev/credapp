package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.CriminalCharge;
import dev.bryrich.credapp.entity.enums.ChargeClassification;
import dev.bryrich.credapp.entity.enums.ChargeStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CriminalChargeFormTest {

    private CriminalChargeForm form() {
        CriminalChargeForm form = new CriminalChargeForm();
        form.setClassification(ChargeClassification.MISDEMEANOR);
        form.setStatus(ChargeStatus.DISMISSED);
        return form;
    }

    @Test
    void allowsFilingOnOrAfterTheIncident() {
        CriminalChargeForm form = form();
        form.setIncidentDate(LocalDate.of(2024, 3, 1));
        form.setDateOfFiling(LocalDate.of(2024, 3, 15));
        assertThat(form.isFilingOnOrAfterIncident()).isTrue();

        form.setDateOfFiling(form.getIncidentDate());
        assertThat(form.isFilingOnOrAfterIncident()).isTrue();
    }

    @Test
    void rejectsAFilingDateBeforeTheIncident() {
        CriminalChargeForm form = form();
        form.setIncidentDate(LocalDate.of(2024, 3, 15));
        form.setDateOfFiling(LocalDate.of(2024, 3, 1));

        assertThat(form.isFilingOnOrAfterIncident()).isFalse();
    }

    @Test
    void allowsEitherDateToBeUnknown() {
        CriminalChargeForm form = form();
        assertThat(form.isFilingOnOrAfterIncident()).isTrue();

        form.setIncidentDate(LocalDate.of(2024, 3, 1));
        assertThat(form.isFilingOnOrAfterIncident()).isTrue();
    }

    @Test
    void blanksBecomeNullRatherThanEmptyStrings() {
        CriminalChargeForm form = form();
        form.setCaseNumber("   ");
        form.setCourt("");
        form.setStatutoryCitation("MCL 750.81");

        CriminalCharge charge = form.toEntity();

        assertThat(charge.getCaseNumber()).isNull();
        assertThat(charge.getCourt()).isNull();
        assertThat(charge.getStatutoryCitation()).isEqualTo("MCL 750.81");
    }

    @Test
    void roundTripsThroughFromAndToEntity() {
        CriminalChargeForm form = form();
        form.setClassification(ChargeClassification.FELONY);
        form.setStatus(ChargeStatus.EXPUNGED);
        form.setIncidentDate(LocalDate.of(2019, 7, 4));
        form.setCaseNumber("19-CR-001");

        CriminalChargeForm round = CriminalChargeForm.from(form.toEntity());

        assertThat(round.getClassification()).isEqualTo(ChargeClassification.FELONY);
        assertThat(round.getStatus()).isEqualTo(ChargeStatus.EXPUNGED);
        assertThat(round.getIncidentDate()).isEqualTo(LocalDate.of(2019, 7, 4));
        assertThat(round.getCaseNumber()).isEqualTo("19-CR-001");
    }
}
