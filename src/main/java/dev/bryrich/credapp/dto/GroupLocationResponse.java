package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupLocation;

import java.time.Instant;
import java.util.List;

public record GroupLocationResponse(
        Long id,
        Long groupId,
        String locationName,
        String address,
        String faxNumber,
        String phoneNumber,
        String handicapAccess,
        List<String> languages,
        Instant createdAt
) {
    public static GroupLocationResponse from(GroupLocation l) {
        return new GroupLocationResponse(l.getId(), l.getGroup().getId(), l.getLocationName(),
                l.getAddress(), l.getFaxNumber(), l.getPhoneNumber(), l.getHandicapAccess(),
                l.getLanguages(), l.getCreatedAt());
    }
}
