package dev.bryrich.credapp.payer;

import java.time.Instant;

public record PayerContactResponse(
        Long id,
        Long payerId,
        Long groupId,
        Long providerId,
        String scope,
        String role,
        String phoneNumber,
        String faxNumber,
        String emailAddress,
        String address,
        Instant createdAt
) {
    public static PayerContactResponse from(PayerContact c) {
        Long groupId = c.getGroup() == null ? null : c.getGroup().getId();
        Long providerId = c.getProvider() == null ? null : c.getProvider().getId();
        String scope = groupId != null ? "GROUP" : providerId != null ? "PROVIDER" : "PAYER";
        return new PayerContactResponse(c.getId(), c.getPayer().getId(), groupId, providerId,
                scope, c.getRole(), c.getPhoneNumber(), c.getFaxNumber(),
                c.getEmailAddress(), c.getAddress(), c.getCreatedAt());
    }
}
