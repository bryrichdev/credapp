package dev.bryrich.credapp.taxonomy;

import java.time.Instant;

public record ProviderTaxonomyResponse(
        Long providerId,
        String code,
        String specialty,
        String grouping,
        boolean primary,
        Instant createdAt
) {
    public static ProviderTaxonomyResponse from(ProviderTaxonomy pt) {
        return new ProviderTaxonomyResponse(pt.getProvider().getId(),
                pt.getTaxonomy().getCode(), pt.getTaxonomy().getSpecialty(),
                pt.getTaxonomy().getGrouping(), pt.isPrimary(), pt.getCreatedAt());
    }
}
