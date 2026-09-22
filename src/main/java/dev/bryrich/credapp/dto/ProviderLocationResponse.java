package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.ProviderLocation;
import dev.bryrich.credapp.entity.enums.PcpScp;

import java.time.Instant;

public record ProviderLocationResponse(
        Long locationId,
        Long providerId,
        Long groupId,
        String locationName,
        PcpScp pcpScp,
        Instant createdAt
) {
    public static ProviderLocationResponse from(ProviderLocation pl) {
        return new ProviderLocationResponse(pl.getLocation().getId(), pl.getProvider().getId(),
                pl.getGroupId(), pl.getLocation().getLocationName(), pl.getPcpScp(),
                pl.getCreatedAt());
    }
}
