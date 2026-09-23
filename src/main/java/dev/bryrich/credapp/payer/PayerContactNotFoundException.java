package dev.bryrich.credapp.payer;

public class PayerContactNotFoundException extends RuntimeException {
    public PayerContactNotFoundException(Long id, Long payerId) {
        super("Contact " + id + " not found for payer " + payerId);
    }
}
