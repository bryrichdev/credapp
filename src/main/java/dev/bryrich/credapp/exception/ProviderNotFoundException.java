package dev.bryrich.credapp.exception;

public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(Long id) {
        super("Provider not found: " + id);
    }
}
