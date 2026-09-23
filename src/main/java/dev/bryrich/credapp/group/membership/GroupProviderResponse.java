package dev.bryrich.credapp.group.membership;

import dev.bryrich.credapp.provider.Provider;

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
