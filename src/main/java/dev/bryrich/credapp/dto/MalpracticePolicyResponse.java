package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.enums.CoverageScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MalpracticePolicyResponse(
        Long id,
        Long providerId,
        Long groupId,
        String policyNumber,
        String carrierName,
        String typeOfCoverage,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        LocalDate originalEffectiveDate,
        BigDecimal amountOfCoveragePerOccurrence,
        BigDecimal amountOfCoveragePerAggregate,
        CoverageScope sharedIndividual,
        Instant createdAt,
        Instant updatedAt
) {
    public static MalpracticePolicyResponse from(MalpracticePolicy p) {
        return new MalpracticePolicyResponse(p.getId(),
                p.getProvider() == null ? null : p.getProvider().getId(),
                p.getGroup() == null ? null : p.getGroup().getId(),
                p.getPolicyNumber(), p.getCarrierName(), p.getTypeOfCoverage(),
                p.getEffectiveDate(), p.getExpirationDate(), p.getOriginalEffectiveDate(),
                p.getAmountOfCoveragePerOccurrence(), p.getAmountOfCoveragePerAggregate(),
                p.getSharedIndividual(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
