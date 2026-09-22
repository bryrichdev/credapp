package dev.bryrich.credapp.exception;

public class ProviderReferenceNotFoundException extends RuntimeException {
    public ProviderReferenceNotFoundException(Long id, Long providerId) {
        super("Reference " + id + " not found for provider " + providerId);
    }
}
