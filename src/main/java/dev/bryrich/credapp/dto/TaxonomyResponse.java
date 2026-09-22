package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Taxonomy;

public record TaxonomyResponse(
        String code,
        String specialty,
        String grouping,
        String label
) {
    public static TaxonomyResponse from(Taxonomy t) {
        return new TaxonomyResponse(t.getCode(), t.getSpecialty(), t.getGrouping(), t.getLabel());
    }
}
