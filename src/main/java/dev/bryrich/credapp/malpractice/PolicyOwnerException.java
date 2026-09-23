package dev.bryrich.credapp.malpractice;

/** A malpractice policy covers exactly one provider or one group, never both or neither. */
public class PolicyOwnerException extends RuntimeException {
    public PolicyOwnerException() {
        super("A malpractice policy must name exactly one provider or one group");
    }
}
