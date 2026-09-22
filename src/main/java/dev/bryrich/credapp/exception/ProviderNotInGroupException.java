package dev.bryrich.credapp.exception;

/**
 * A provider can only be assigned to a location of a group they belong to. The database
 * enforces this through a composite foreign key; this turns that into a message a
 * coordinator can act on rather than a constraint violation.
 */
public class ProviderNotInGroupException extends RuntimeException {
    public ProviderNotInGroupException(Long providerId, Long groupId) {
        super("Provider " + providerId + " is not assigned to group " + groupId
                + ", so they cannot be placed at its locations");
    }
}
