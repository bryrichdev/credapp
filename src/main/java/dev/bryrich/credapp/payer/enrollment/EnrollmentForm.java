package dev.bryrich.credapp.payer.enrollment;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * One payer row on the provider or group form. A row is identified by its payer: each
 * provider or group has at most one enrollment per payer, so saving matches rows to what's
 * on file by payer, updates those, adds the rest, and removes enrollments whose row is gone.
 */
public class EnrollmentForm {

    @NotNull(message = "Payer is required")
    private Long payerId;

    @NotNull(message = "Status is required")
    private EnrollmentStatus status = EnrollmentStatus.NOT_STARTED;

    @Size(max = 100, message = "Payer-assigned ID can be at most 100 characters")
    private String payerAssignedId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate submittedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    @Size(max = 2000, message = "Notes can be at most 2000 characters")
    private String notes;

    protected void copyFrom(Enrollment enrollment) {
        payerId = enrollment.getPayer().getId();
        status = enrollment.getStatus();
        payerAssignedId = enrollment.getPayerAssignedId();
        submittedDate = enrollment.getSubmittedDate();
        effectiveDate = enrollment.getEffectiveDate();
        notes = enrollment.getNotes();
    }

    public void applyTo(Enrollment enrollment) {
        enrollment.setStatus(status);
        enrollment.setPayerAssignedId(blankToNull(payerAssignedId));
        enrollment.setSubmittedDate(submittedDate);
        enrollment.setEffectiveDate(effectiveDate);
        enrollment.setNotes(blankToNull(notes));
    }

    /** An active enrollment has a date it took effect; the database holds to this too. */
    @AssertTrue(message = "Add the effective date for an active enrollment")
    public boolean isEffectiveDateSetWhenActive() {
        return status != EnrollmentStatus.ACTIVE || effectiveDate != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getPayerId() {
        return payerId;
    }

    public void setPayerId(Long payerId) {
        this.payerId = payerId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }

    public String getPayerAssignedId() {
        return payerAssignedId;
    }

    public void setPayerAssignedId(String payerAssignedId) {
        this.payerAssignedId = payerAssignedId;
    }

    public LocalDate getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDate submittedDate) {
        this.submittedDate = submittedDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
