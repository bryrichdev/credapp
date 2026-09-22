package dev.bryrich.credapp.exception;

public class TaxonomyNotFoundException extends RuntimeException {
    public TaxonomyNotFoundException(String code) {
        super("Taxonomy code not found: " + code);
    }
}
