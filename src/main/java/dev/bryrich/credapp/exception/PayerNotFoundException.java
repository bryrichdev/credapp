package dev.bryrich.credapp.exception;

public class PayerNotFoundException extends RuntimeException {
    public PayerNotFoundException(Long id) {
        super("Payer not found: " + id);
    }
}
