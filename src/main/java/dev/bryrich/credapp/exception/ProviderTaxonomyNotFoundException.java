package dev.bryrich.credapp.exception;

public class ProviderTaxonomyNotFoundException extends RuntimeException {
    public ProviderTaxonomyNotFoundException(Long providerId, String code) {
        super("Taxonomy " + code + " not found for provider " + providerId);
    }
}
