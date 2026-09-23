package dev.bryrich.credapp.group;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long id) {
        super("Group not found: " + id);
    }
}
