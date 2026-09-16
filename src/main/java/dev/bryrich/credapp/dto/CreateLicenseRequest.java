package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.License;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateLicenseRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String state,
        @NotBlank String licenseNumber,
        @NotBlank String licenseType,
        LocalDate issueDate,
        @NotNull LocalDate expirationDate,
        @NotBlank
        @Pattern(regexp = "^(active|expired|suspended|revoked|surrendered|probation|inactive|pending)$")
        String status,
        String restrictions
) {
    public License toEntity() {
        License l = new License(state, licenseNumber, licenseType, expirationDate, status);
        l.setIssueDate(issueDate);
        l.setRestrictions(restrictions);
        return l;
    }
}