package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.MalpracticeClaim;
import jakarta.validation.constraints.NotBlank;

public class MalpracticeClaimForm {

    @NotBlank(message = "Claim number is required")
    private String claimNumber;

    @NotBlank(message = "Carrier is required")
    private String carrierName;

    /** Optional. Claims often surface from a carrier before the policy is on file. */
    private Long policyId;

    private String outcome;

    /** Empty form, for the create screen. */
    public MalpracticeClaimForm() {
    }

    /** Copies a saved claim's values in, so the edit screen renders them. */
    public static MalpracticeClaimForm from(MalpracticeClaim claim) {
        MalpracticeClaimForm form = new MalpracticeClaimForm();
        form.claimNumber = claim.getClaimNumber();
        form.carrierName = claim.getCarrierName();
        form.policyId = claim.getPolicy() == null ? null : claim.getPolicy().getId();
        form.outcome = claim.getOutcome();
        return form;
    }

    public MalpracticeClaim toEntity() {
        MalpracticeClaim claim = new MalpracticeClaim(claimNumber, carrierName);
        claim.setOutcome(blankToNull(outcome));
        return claim;
    }

    /** Copies this form's values onto an existing claim. The policy link is set by the service. */
    public void applyTo(MalpracticeClaim claim) {
        claim.setClaimNumber(claimNumber);
        claim.setCarrierName(carrierName);
        claim.setOutcome(blankToNull(outcome));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
