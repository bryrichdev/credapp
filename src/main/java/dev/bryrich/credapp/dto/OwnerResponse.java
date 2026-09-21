package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Owner;

import java.time.Instant;
import java.time.LocalDate;

/** Deliberately carries no SSN, matching ProviderResponse. */
public record OwnerResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate dob,
        String homeAddress,
        Instant createdAt
) {
    public static OwnerResponse from(Owner o) {
        return new OwnerResponse(o.getId(), o.getFirstName(), o.getLastName(),
                o.getDob(), o.getHomeAddress(), o.getCreatedAt());
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
