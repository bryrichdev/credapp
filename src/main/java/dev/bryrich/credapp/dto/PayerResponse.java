package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Payer;

import java.time.Instant;

public record PayerResponse(
        Long id,
        String name,
        String note,
        Instant createdAt
) {
    public static PayerResponse from(Payer p) {
        return new PayerResponse(p.getId(), p.getName(), p.getNote(), p.getCreatedAt());
    }
}
