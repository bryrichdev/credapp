package dev.bryrich.credapp.passwordreset;

/** The reset link is wrong, already used, or expired. */
public class InvalidResetLinkException extends RuntimeException {
    public InvalidResetLinkException() {
        super("This reset link is invalid or has expired");
    }
}
