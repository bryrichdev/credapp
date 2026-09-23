package dev.bryrich.credapp.taxonomy;

public class TaxonomyNotFoundException extends RuntimeException {
    public TaxonomyNotFoundException(String code) {
        super("Taxonomy code not found: " + code);
    }
}
