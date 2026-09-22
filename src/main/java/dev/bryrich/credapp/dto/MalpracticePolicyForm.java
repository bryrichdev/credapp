package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy covers one provider or one group. Both ids are on the form because the same
 * screen serves either case; exactly one must be filled, which the database also enforces.
 */
public class MalpracticePolicyForm {

    @NotBlank(message = "Policy number is required")
    private String policyNumber;

    @NotBlank(message = "Carrier is required")
    private String carrierName;

    @NotBlank(message = "Type of coverage is required")
    private String typeOfCoverage;

    @NotNull(message = "Effective date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expirationDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate originalEffectiveDate;

    @DecimalMin(value = "0.00", message = "Coverage cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Coverage allows at most two decimal places")
    private BigDecimal amountOfCoveragePerOccurrence;

    @DecimalMin(value = "0.00", message = "Coverage cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Coverage allows at most two decimal places")
    private BigDecimal amountOfCoveragePerAggregate;

    @NotNull(message = "Shared or individual is required")
    private CoverageScope sharedIndividual;

    private Long providerId;
    private Long groupId;

    /** Empty form, for the create screen. */
    public MalpracticePolicyForm() {
    }

    /** Copies a saved policy's values in, so the edit screen renders them. */
    public static MalpracticePolicyForm from(MalpracticePolicy policy) {
        MalpracticePolicyForm form = new MalpracticePolicyForm();
        form.policyNumber = policy.getPolicyNumber();
        form.carrierName = policy.getCarrierName();
        form.typeOfCoverage = policy.getTypeOfCoverage();
        form.effectiveDate = policy.getEffectiveDate();
        form.expirationDate = policy.getExpirationDate();
        form.originalEffectiveDate = policy.getOriginalEffectiveDate();
        form.amountOfCoveragePerOccurrence = policy.getAmountOfCoveragePerOccurrence();
        form.amountOfCoveragePerAggregate = policy.getAmountOfCoveragePerAggregate();
        form.sharedIndividual = policy.getSharedIndividual();
        form.providerId = policy.getProvider() == null ? null : policy.getProvider().getId();
        form.groupId = policy.getGroup() == null ? null : policy.getGroup().getId();
        return form;
    }

    /** Copies this form's values onto an existing policy. The owner is never reassigned. */
    public void applyTo(MalpracticePolicy policy) {
        policy.setPolicyNumber(policyNumber);
        policy.setCarrierName(carrierName);
        policy.setTypeOfCoverage(typeOfCoverage);
        policy.setEffectiveDate(effectiveDate);
        policy.setExpirationDate(expirationDate);
        policy.setOriginalEffectiveDate(originalEffectiveDate);
        policy.setAmountOfCoveragePerOccurrence(amountOfCoveragePerOccurrence);
        policy.setAmountOfCoveragePerAggregate(amountOfCoveragePerAggregate);
        policy.setSharedIndividual(sharedIndividual);
    }

    @AssertTrue(message = "A policy must name exactly one provider or one group")
    public boolean isOwnerExclusive() {
        return (providerId == null) != (groupId == null);
    }

    @AssertTrue(message = "Expiration date must be after the effective date")
    public boolean isExpirationAfterEffective() {
        return expirationDate == null || effectiveDate == null
                || expirationDate.isAfter(effectiveDate);
    }

    @AssertTrue(message = "Original effective date cannot be after the effective date")
    public boolean isOriginalOnOrBeforeEffective() {
        return originalEffectiveDate == null || effectiveDate == null
                || !originalEffectiveDate.isAfter(effectiveDate);
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
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

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
