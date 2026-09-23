package dev.bryrich.credapp.provider.certification;

public class CertificationNotFoundException extends RuntimeException {
    public CertificationNotFoundException(Long id, Long providerId) {
        super("Certification " + id + " not found for provider " + providerId);
    }
}
