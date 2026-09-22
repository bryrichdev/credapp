package dev.bryrich.credapp.exception;

public class MalpracticePolicyNotFoundException extends RuntimeException {
    public MalpracticePolicyNotFoundException(Long id) {
        super("Malpractice policy not found: " + id);
    }
}
