package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.LicenseStatus;

import java.time.Instant;
import java.time.LocalDate;

public record LicenseResponse(
        Long id,
        Long providerId,
        String state,
        String licenseNumber,
        String licenseType,
        LocalDate issueDate,
        LocalDate expirationDate,
        LicenseStatus status,
        String restrictions,
        Instant createdAt,
        Instant updatedAt
) {
    public static LicenseResponse from(License l) {
        return new LicenseResponse(l.getId(), l.getProvider().getId(), l.getState(), l.getLicenseNumber(),
                l.getLicenseType(), l.getIssueDate(), l.getExpirationDate(), l.getStatus(),
                l.getRestrictions(), l.getCreatedAt(), l.getUpdatedAt());
    }
}
