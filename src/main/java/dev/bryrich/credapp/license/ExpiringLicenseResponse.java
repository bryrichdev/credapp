package dev.bryrich.credapp.license;

import dev.bryrich.credapp.provider.Provider;

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
