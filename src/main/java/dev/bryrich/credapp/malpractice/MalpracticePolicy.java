package dev.bryrich.credapp.malpractice;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A malpractice policy covering either one provider or one group, never both and never
 * neither — a CHECK constraint enforces that, so use the two static factories rather than
 * setting the owner by hand.
 */
@Entity
@Table(name = "malpractice_policies")
public class MalpracticePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false)
    private String policyNumber;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDate expirationDate;
    private LocalDate originalEffectiveDate;

    @Column(nullable = false)
    private String carrierName;

    @Column(nullable = false)
    private String typeOfCoverage;

    private BigDecimal amountOfCoveragePerOccurrence;
    private BigDecimal amountOfCoveragePerAggregate;

    @Column(nullable = false)
    private CoverageScope sharedIndividual;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected MalpracticePolicy() {}

    private MalpracticePolicy(String policyNumber, String carrierName, String typeOfCoverage,
                              LocalDate effectiveDate, CoverageScope sharedIndividual) {
        this.policyNumber = policyNumber;
        this.carrierName = carrierName;
        this.typeOfCoverage = typeOfCoverage;
        this.effectiveDate = effectiveDate;
        this.sharedIndividual = sharedIndividual;
    }

    public static MalpracticePolicy forProvider(Provider provider, String policyNumber,
                                                String carrierName, String typeOfCoverage,
                                                LocalDate effectiveDate, CoverageScope scope) {
        MalpracticePolicy policy = new MalpracticePolicy(policyNumber, carrierName,
                typeOfCoverage, effectiveDate, scope);
        policy.provider = provider;
        return policy;
    }

    public static MalpracticePolicy forGroup(Group group, String policyNumber,
                                             String carrierName, String typeOfCoverage,
                                             LocalDate effectiveDate, CoverageScope scope) {
        MalpracticePolicy policy = new MalpracticePolicy(policyNumber, carrierName,
                typeOfCoverage, effectiveDate, scope);
        policy.group = group;
        return policy;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public Group getGroup() {
        return group;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public LocalDate getOriginalEffectiveDate() {
        return originalEffectiveDate;
    }

    public void setOriginalEffectiveDate(LocalDate originalEffectiveDate) {
        this.originalEffectiveDate = originalEffectiveDate;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getTypeOfCoverage() {
        return typeOfCoverage;
    }

    public void setTypeOfCoverage(String typeOfCoverage) {
        this.typeOfCoverage = typeOfCoverage;
    }

    public BigDecimal getAmountOfCoveragePerOccurrence() {
        return amountOfCoveragePerOccurrence;
    }

    public void setAmountOfCoveragePerOccurrence(BigDecimal amount) {
        this.amountOfCoveragePerOccurrence = amount;
    }

    public BigDecimal getAmountOfCoveragePerAggregate() {
        return amountOfCoveragePerAggregate;
    }

    public void setAmountOfCoveragePerAggregate(BigDecimal amount) {
        this.amountOfCoveragePerAggregate = amount;
    }

    public CoverageScope getSharedIndividual() {
        return sharedIndividual;
    }

    public void setSharedIndividual(CoverageScope sharedIndividual) {
        this.sharedIndividual = sharedIndividual;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
