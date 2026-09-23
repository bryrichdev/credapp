package dev.bryrich.credapp.provider.location;

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
