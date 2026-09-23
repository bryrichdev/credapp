package dev.bryrich.credapp.provider.disclosure;

import java.time.Instant;
import java.time.LocalDate;

public record CriminalChargeResponse(
        Long id,
        Long providerId,
        ChargeClassification classification,
        ChargeStatus status,
        LocalDate incidentDate,
        LocalDate dateOfFiling,
        String caseNumber,
        String court,
        String statutoryCitation,
        String sentencingTerms,
        Instant createdAt,
        Instant updatedAt
) {
    public static CriminalChargeResponse from(CriminalCharge c) {
        return new CriminalChargeResponse(c.getId(), c.getProvider().getId(),
                c.getClassification(), c.getStatus(), c.getIncidentDate(), c.getDateOfFiling(),
                c.getCaseNumber(), c.getCourt(), c.getStatutoryCitation(),
                c.getSentencingTerms(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
