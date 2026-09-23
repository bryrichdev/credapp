package dev.bryrich.credapp.group.location;

public class GroupLocationNotFoundException extends RuntimeException {
    public GroupLocationNotFoundException(Long id, Long groupId) {
        super("Location " + id + " not found for group " + groupId);
    }

    /** For lookups by location id alone, where no group is in hand to name. */
    public GroupLocationNotFoundException(Long id) {
        super("Location not found: " + id);
    }
}
