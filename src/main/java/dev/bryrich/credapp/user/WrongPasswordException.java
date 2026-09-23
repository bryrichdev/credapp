package dev.bryrich.credapp.user;

/** The current password given to confirm a change to your own account didn't match. */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("That isn't your current password");
    }
}
