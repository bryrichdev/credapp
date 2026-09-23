package dev.bryrich.credapp.taxonomy;

public class GroupTaxonomyNotFoundException extends RuntimeException {
    public GroupTaxonomyNotFoundException(Long groupId, String code) {
        super("Taxonomy " + code + " not found for group " + groupId);
    }
}
