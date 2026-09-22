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
        String street1,
        String street2,
        String city,
        String state,
        String zipCode,
        Instant createdAt
) {
    public static OwnerResponse from(Owner o) {
        return new OwnerResponse(o.getId(), o.getFirstName(), o.getLastName(),
                o.getDob(), o.getStreet1(), o.getStreet2(), o.getCity(),
                o.getState(), o.getZipCode(), o.getCreatedAt());
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
