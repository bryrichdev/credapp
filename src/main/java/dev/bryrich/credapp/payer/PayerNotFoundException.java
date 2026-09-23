package dev.bryrich.credapp.payer;

public class PayerNotFoundException extends RuntimeException {
    public PayerNotFoundException(Long id) {
        super("Payer not found: " + id);
    }
}
