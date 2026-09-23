package dev.bryrich.credapp.owner;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GroupOwnerResponse(
        Long groupId,
        Long ownerId,
        String ownerName,
        BigDecimal percentOwned,
        LocalDate effectiveDate
) {
    public static GroupOwnerResponse from(GroupOwner go) {
        Owner o = go.getOwner();
        return new GroupOwnerResponse(go.getGroup().getId(), o.getId(),
                o.getFirstName() + " " + o.getLastName(),
                go.getPercentOwned(), go.getEffectiveDate());
    }
}
