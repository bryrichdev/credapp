package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.ProviderReference;

import java.time.Instant;

public record ProviderReferenceResponse(
        Long id,
        Long providerId,
        String name,
        String title,
        String relationship,
        String emailAddress,
        String street1,
        String street2,
        String city,
        String state,
        String zipCode,
        String phoneNumber,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProviderReferenceResponse from(ProviderReference r) {
        return new ProviderReferenceResponse(r.getId(), r.getProvider().getId(), r.getName(),
                r.getTitle(), r.getRelationship(), r.getEmailAddress(), r.getStreet1(),
                r.getStreet2(), r.getCity(), r.getState(), r.getZipCode(), r.getPhoneNumber(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
