package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupLocation;

import java.time.Instant;

public record GroupLocationResponse(
        Long id,
        Long groupId,
        String locationName,
        String address,
        String faxNumber,
        String phoneNumber,
        String handicapAccess,
        String languages,
        Instant createdAt
) {
    public static GroupLocationResponse from(GroupLocation l) {
        return new GroupLocationResponse(l.getId(), l.getGroup().getId(), l.getLocationName(),
                l.getAddress(), l.getFaxNumber(), l.getPhoneNumber(), l.getHandicapAccess(),
                l.getLanguages(), l.getCreatedAt());
    }
}
