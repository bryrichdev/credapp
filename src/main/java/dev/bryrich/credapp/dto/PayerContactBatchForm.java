package dev.bryrich.credapp.dto;

import jakarta.validation.Valid;
import org.springframework.util.AutoPopulatingList;

import java.util.List;

/**
 * Holds however many contacts the page decided to show. AutoPopulatingList grows as
 * Spring binds contacts[0], contacts[1] and so on, so the page can add blocks without the
 * server knowing in advance how many are coming.
 */
public class PayerContactBatchForm {

    @Valid
    private List<PayerContactForm> contacts =
            new AutoPopulatingList<>(PayerContactForm.class);

    public PayerContactBatchForm() {
        contacts.add(new PayerContactForm());
    }

    /**
     * Drops blocks nobody typed into, so an added-then-abandoned block doesn't fail
     * validation. Always leaves one behind, which then reports its own missing role.
     */
    public void pruneBlank() {
        contacts.removeIf(contact -> contact == null || contact.isBlank());
        if (contacts.isEmpty()) {
            contacts.add(new PayerContactForm());
        }
    }

    public List<PayerContactForm> getContacts() {
        return contacts;
    }

    public void setContacts(List<PayerContactForm> contacts) {
        this.contacts = contacts;
    }
}
