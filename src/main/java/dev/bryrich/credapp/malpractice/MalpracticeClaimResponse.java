package dev.bryrich.credapp.malpractice;

import java.time.Instant;

public record MalpracticeClaimResponse(
        Long id,
        Long providerId,
        Long policyId,
        String claimNumber,
        String carrierName,
        String outcome,
        Instant createdAt,
        Instant updatedAt
) {
    public static MalpracticeClaimResponse from(MalpracticeClaim c) {
        return new MalpracticeClaimResponse(c.getId(), c.getProvider().getId(),
                c.getPolicy() == null ? null : c.getPolicy().getId(),
                c.getClaimNumber(), c.getCarrierName(), c.getOutcome(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
