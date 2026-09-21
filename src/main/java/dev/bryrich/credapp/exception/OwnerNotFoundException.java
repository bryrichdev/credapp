package dev.bryrich.credapp.exception;

public class OwnerNotFoundException extends RuntimeException {
    public OwnerNotFoundException(Long id) {
        super("Owner not found: " + id);
    }

    public OwnerNotFoundException(Long ownerId, Long groupId) {
        super("Owner " + ownerId + " is not an owner of group " + groupId);
    }
}
