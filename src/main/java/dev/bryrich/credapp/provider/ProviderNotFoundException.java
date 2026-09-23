package dev.bryrich.credapp.provider;

public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(Long id) {
        super("Provider not found: " + id);
    }
}
