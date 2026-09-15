package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.Provider;

import java.time.LocalDate;

public record ExpiringLicenseResponse(
        Long licenseId,
        Long providerId,
        String providerName,
        String state,
        String licenseNumber,
        String licenseType,
        LocalDate expirationDate
) {
    public static ExpiringLicenseResponse from(License l) {
        Provider p = l.getProvider();
        return new ExpiringLicenseResponse(l.getId(), p.getId(),
                p.getFirstName() + " " + p.getLastName(),
                l.getState(), l.getLicenseNumber(), l.getLicenseType(),
                l.getExpirationDate());
    }
}
