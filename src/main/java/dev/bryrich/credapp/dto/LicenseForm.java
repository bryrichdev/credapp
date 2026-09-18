package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.LicenseStatus;
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

    private String restrictions;

    public License toEntity() {
        License license = new License(
                state == null ? null : state.trim().toUpperCase(Locale.ROOT),
                licenseNumber,
                licenseType,
                expirationDate,
                status);
        license.setIssueDate(issueDate);
        license.setRestrictions(restrictions == null || restrictions.isBlank() ? null : restrictions);
        return license;
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
}