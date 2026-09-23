package dev.bryrich.credapp.provider.privilege;

public class HospitalPrivilegeNotFoundException extends RuntimeException {
    public HospitalPrivilegeNotFoundException(Long id, Long providerId) {
        super("Hospital privilege " + id + " not found for provider " + providerId);
    }
}
