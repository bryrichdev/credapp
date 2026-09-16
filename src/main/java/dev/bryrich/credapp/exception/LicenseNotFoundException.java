package dev.bryrich.credapp.exception;

public class LicenseNotFoundException extends RuntimeException {
    public LicenseNotFoundException(Long id, Long providerId) {
        super("License " + id + " not found for provider " + providerId);
    }
}
