package dev.bryrich.credapp.ssn;

/** Raised when the signed-in account isn't allowed to decrypt a stored SSN. */
public class SsnAccessDeniedException extends RuntimeException {
    public SsnAccessDeniedException(String message) {
        super(message);
    }
}
