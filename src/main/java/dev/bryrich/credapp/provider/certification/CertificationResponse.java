package dev.bryrich.credapp.provider.certification;

import java.time.Instant;
import java.time.LocalDate;

public record CertificationResponse(
        Long id,
        Long providerId,
        String board,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        boolean lifetime,
        Instant createdAt,
        Instant updatedAt
) {
    public static CertificationResponse from(Certification c) {
        return new CertificationResponse(c.getId(), c.getProvider().getId(), c.getBoard(),
                c.getEffectiveDate(), c.getExpirationDate(), c.isLifetime(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
