package dev.bryrich.credapp.exception;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long id) {
        super("Group not found: " + id);
    }
}
