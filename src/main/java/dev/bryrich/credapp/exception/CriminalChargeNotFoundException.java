package dev.bryrich.credapp.exception;

public class CriminalChargeNotFoundException extends RuntimeException {
    public CriminalChargeNotFoundException(Long id, Long providerId) {
        super("Criminal charge " + id + " not found for provider " + providerId);
    }
}
