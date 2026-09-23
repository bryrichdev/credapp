package dev.bryrich.credapp.taxonomy;

public class ProviderTaxonomyNotFoundException extends RuntimeException {
    public ProviderTaxonomyNotFoundException(Long providerId, String code) {
        super("Taxonomy " + code + " not found for provider " + providerId);
    }
}
