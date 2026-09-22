package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Group;

import java.time.Instant;

public record GroupResponse(
        Long id,
        String lbn,
        String dba,
        String npi,
        String taxId,
        Instant createdAt
) {
    public static GroupResponse from(Group g) {
        return new GroupResponse(g.getId(), g.getLbn(), g.getDba(), g.getNpi(),
                g.getTaxId(), g.getCreatedAt());
    }
}
