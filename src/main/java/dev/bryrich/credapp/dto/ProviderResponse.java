package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.Sex;

import java.time.Instant;
import java.time.LocalDate;

public record ProviderResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate dob,
        String npi,
        Sex sex,
        String phoneNumber,
        Instant createdAt
) {
    public static ProviderResponse from(Provider p) {
        return new ProviderResponse(p.getId(), p.getFirstName(), p.getLastName(),
                p.getDob(), p.getNpi(), p.getSex(), p.getPhoneNumber(), p.getCreatedAt());
    }
}
