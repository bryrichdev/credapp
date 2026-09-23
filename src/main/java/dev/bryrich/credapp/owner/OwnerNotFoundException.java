package dev.bryrich.credapp.owner;

public class OwnerNotFoundException extends RuntimeException {
    public OwnerNotFoundException(Long id) {
        super("Owner not found: " + id);
    }

    public OwnerNotFoundException(Long ownerId, Long groupId) {
        super("Owner " + ownerId + " is not an owner of group " + groupId);
    }
}
