package dev.bryrich.credapp.exception;

/** Raised when the signed-in account isn't allowed to act on the account it is targeting. */
public class UserManagementDeniedException extends RuntimeException {
    public UserManagementDeniedException(String message) {
        super(message);
    }
}
