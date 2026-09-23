package dev.bryrich.credapp.malpractice;

public class MalpracticePolicyNotFoundException extends RuntimeException {
    public MalpracticePolicyNotFoundException(Long id) {
        super("Malpractice policy not found: " + id);
    }
}
