package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.enums.LicenseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

public class LicenseForm {

    @NotBlank(message = "State is required")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Use the two-letter state code")
    private String state;

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "License type is required")
    private String licenseType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;

    @NotNull(message = "Expiration date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expirationDate;

    @NotNull(message = "Status is required")
    private LicenseStatus status = LicenseStatus.ACTIVE;

    /** Required on the Licenses tab form; the provider form supplies it from the path. */
    private Long providerId;

    private String restrictions;

    /** Empty form, for the create screen. */
    public LicenseForm() {
    }

    /** Copies a saved license's values in, so the edit screen renders them. */
    public static LicenseForm from(License license) {
        LicenseForm form = new LicenseForm();
        form.state = license.getState();
        form.licenseNumber = license.getLicenseNumber();
        form.licenseType = license.getLicenseType();
        form.issueDate = license.getIssueDate();
        form.expirationDate = license.getExpirationDate();
        form.status = license.getStatus();
        form.restrictions = license.getRestrictions();
        return form;
    }

    public License toEntity() {
        License license = new License(
                normalizedState(),
                licenseNumber,
                licenseType,
                expirationDate,
                status);
        license.setIssueDate(issueDate);
        license.setRestrictions(blankToNull(restrictions));
        return license;
    }

    /** Copies this form's values onto an existing license, for updates. */
    public void applyTo(License license) {
        license.setState(normalizedState());
        license.setLicenseNumber(licenseNumber);
        license.setLicenseType(licenseType);
        license.setIssueDate(issueDate);
        license.setExpirationDate(expirationDate);
        license.setStatus(status);
        license.setRestrictions(blankToNull(restrictions));
    }

    private String normalizedState() {
        return state == null ? null : state.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
    }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
}
