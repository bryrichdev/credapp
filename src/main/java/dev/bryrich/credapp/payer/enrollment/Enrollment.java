package dev.bryrich.credapp.payer.enrollment;

import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.usergroup.GroupScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** What a provider's and a group's enrollment with a payer have in common. */
@MappedSuperclass
public abstract class Enrollment extends GroupScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false, updatable = false)
    private Payer payer;

    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.NOT_STARTED;

    /** The ID the payer assigned once enrolled: a provider or group number with that payer. */
    private String payerAssignedId;

    private LocalDate submittedDate;

    private LocalDate effectiveDate;

    /** When the payer expects to recredential or revalidate this enrollment. */
    private LocalDate recredentialDate;

    /** A date someone set to chase the payer about this enrollment. */
    private LocalDate followUpDate;

    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Enrollment() {
    }

    protected Enrollment(Payer payer) {
        this.payer = payer;
    }

    public Long getId() {
        return id;
    }

    public Payer getPayer() {
        return payer;
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

    public LocalDate getRecredentialDate() {
        return recredentialDate;
    }

    public void setRecredentialDate(LocalDate recredentialDate) {
        this.recredentialDate = recredentialDate;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
