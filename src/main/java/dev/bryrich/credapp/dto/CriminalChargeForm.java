package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.CriminalCharge;
import dev.bryrich.credapp.entity.enums.ChargeClassification;
import dev.bryrich.credapp.entity.enums.ChargeStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class CriminalChargeForm {

    @NotNull(message = "Classification is required")
    private ChargeClassification classification;

    @NotNull(message = "Status is required")
    private ChargeStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate incidentDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfFiling;

    private String caseNumber;
    private String court;
    private String statutoryCitation;
    private String sentencingTerms;

    /** Empty form, for the create screen. */
    public CriminalChargeForm() {
    }

    /** Copies a saved charge's values in, so the edit screen renders them. */
    public static CriminalChargeForm from(CriminalCharge charge) {
        CriminalChargeForm form = new CriminalChargeForm();
        form.classification = charge.getClassification();
        form.status = charge.getStatus();
        form.incidentDate = charge.getIncidentDate();
        form.dateOfFiling = charge.getDateOfFiling();
        form.caseNumber = charge.getCaseNumber();
        form.court = charge.getCourt();
        form.statutoryCitation = charge.getStatutoryCitation();
        form.sentencingTerms = charge.getSentencingTerms();
        return form;
    }

    public CriminalCharge toEntity() {
        CriminalCharge charge = new CriminalCharge(classification, status);
        applyTo(charge);
        return charge;
    }

    /** Copies this form's values onto an existing charge, for updates. */
    public void applyTo(CriminalCharge charge) {
        charge.setClassification(classification);
        charge.setStatus(status);
        charge.setIncidentDate(incidentDate);
        charge.setDateOfFiling(dateOfFiling);
        charge.setCaseNumber(blankToNull(caseNumber));
        charge.setCourt(blankToNull(court));
        charge.setStatutoryCitation(blankToNull(statutoryCitation));
        charge.setSentencingTerms(blankToNull(sentencingTerms));
    }

    @AssertTrue(message = "Filing date cannot be before the incident date")
    public boolean isFilingOnOrAfterIncident() {
        return dateOfFiling == null || incidentDate == null
                || !dateOfFiling.isBefore(incidentDate);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public ChargeClassification getClassification() {
        return classification;
    }

    public void setClassification(ChargeClassification classification) {
        this.classification = classification;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public void setStatus(ChargeStatus status) {
        this.status = status;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public void setIncidentDate(LocalDate incidentDate) {
        this.incidentDate = incidentDate;
    }

    public LocalDate getDateOfFiling() {
        return dateOfFiling;
    }

    public void setDateOfFiling(LocalDate dateOfFiling) {
        this.dateOfFiling = dateOfFiling;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getCourt() {
        return court;
    }

    public void setCourt(String court) {
        this.court = court;
    }

    public String getStatutoryCitation() {
        return statutoryCitation;
    }

    public void setStatutoryCitation(String statutoryCitation) {
        this.statutoryCitation = statutoryCitation;
    }

    public String getSentencingTerms() {
        return sentencingTerms;
    }

    public void setSentencingTerms(String sentencingTerms) {
        this.sentencingTerms = sentencingTerms;
    }
}
