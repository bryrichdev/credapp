package dev.bryrich.credapp.group.location;

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
        String street1, String street2, String city, String state, String zipCode,
        Instant createdAt
) {
    public static GroupLocationResponse from(GroupLocation l) {
        return new GroupLocationResponse(l.getId(), l.getGroup().getId(), l.getLocationName(),
                l.getAddress(), l.getFaxNumber(), l.getPhoneNumber(), l.getHandicapAccess(),
                l.getLanguages(), l.getStreet1(), l.getStreet2(), l.getCity(), l.getState(), l.getZipCode(), l.getCreatedAt());
    }
}
