package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupProvider;
import dev.bryrich.credapp.entity.Provider;

import java.time.LocalDate;

public record GroupProviderResponse(
        Long groupId,
        Long providerId,
        String providerName,
        String npi,
        LocalDate effectiveDate
) {
    public static GroupProviderResponse from(GroupProvider gp) {
        Provider p = gp.getProvider();
        return new GroupProviderResponse(gp.getGroup().getId(), p.getId(),
                p.getFirstName() + " " + p.getLastName(), p.getNpi(), gp.getEffectiveDate());
    }
}
