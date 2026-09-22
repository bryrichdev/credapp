package dev.bryrich.credapp.exception;

public class MalpracticeClaimNotFoundException extends RuntimeException {
    public MalpracticeClaimNotFoundException(Long id, Long providerId) {
        super("Malpractice claim " + id + " not found for provider " + providerId);
    }
}
