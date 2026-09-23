package dev.bryrich.credapp.provider.location;

public class ProviderLocationNotFoundException extends RuntimeException {
    public ProviderLocationNotFoundException(Long locationId, Long providerId) {
        super("Provider " + providerId + " is not assigned to location " + locationId);
    }
}
