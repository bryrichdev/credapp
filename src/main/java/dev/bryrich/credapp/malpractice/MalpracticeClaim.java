package dev.bryrich.credapp.malpractice;

import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A claim made against a provider. The carrier is stored on the row because it is what
 * makes the claim number unique; the policy link is optional, since claims often surface
 * from a carrier's history before the matching policy is on file.
 */
@Entity
@Table(name = "malpractice_claims")
public class MalpracticeClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private MalpracticePolicy policy;

    @Column(nullable = false)
    private String claimNumber;

    @Column(nullable = false)
    private String carrierName;

    private String outcome;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected MalpracticeClaim() {}

    public MalpracticeClaim(String claimNumber, String carrierName) {
        this.claimNumber = claimNumber;
        this.carrierName = carrierName;
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

    public MalpracticePolicy getPolicy() {
        return policy;
    }

    public void setPolicy(MalpracticePolicy policy) {
        this.policy = policy;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public void setClaimNumber(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
