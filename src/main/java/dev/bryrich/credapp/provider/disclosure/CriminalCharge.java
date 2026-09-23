package dev.bryrich.credapp.provider.disclosure;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A criminal charge disclosed on a credentialing application. The row is retained on
 * ON DELETE RESTRICT: a disclosure history has to survive an accidental provider delete.
 */
@Entity
@Table(name = "criminal_charges")
public class CriminalCharge extends GroupScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private ChargeClassification classification;

    @Column(nullable = false)
    private ChargeStatus status;

    private LocalDate incidentDate;
    private LocalDate dateOfFiling;
    private String caseNumber;
    private String court;
    private String statutoryCitation;
    private String sentencingTerms;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected CriminalCharge() {}

    public CriminalCharge(ChargeClassification classification, ChargeStatus status) {
        this.classification = classification;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
