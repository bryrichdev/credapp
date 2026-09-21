package dev.bryrich.credapp.exception;

public class GroupLocationNotFoundException extends RuntimeException {
    public GroupLocationNotFoundException(Long id, Long groupId) {
        super("Location " + id + " not found for group " + groupId);
    }
}
