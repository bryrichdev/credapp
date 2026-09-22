package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupTaxonomy;

import java.time.Instant;

public record GroupTaxonomyResponse(
        Long groupId,
        String code,
        String specialty,
        String grouping,
        boolean primary,
        Instant createdAt
) {
    public static GroupTaxonomyResponse from(GroupTaxonomy gt) {
        return new GroupTaxonomyResponse(gt.getGroup().getId(),
                gt.getTaxonomy().getCode(), gt.getTaxonomy().getSpecialty(),
                gt.getTaxonomy().getGrouping(), gt.isPrimary(), gt.getCreatedAt());
    }
}
