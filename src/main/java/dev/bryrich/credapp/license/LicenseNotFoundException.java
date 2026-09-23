package dev.bryrich.credapp.license;

public class LicenseNotFoundException extends RuntimeException {
    public LicenseNotFoundException(Long id) {
        super("License not found: " + id);
    }

    public LicenseNotFoundException(Long id, Long providerId) {
        super("License " + id + " not found for provider " + providerId);
    }
}
