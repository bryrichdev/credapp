package dev.bryrich.credapp.payer;

import jakarta.validation.constraints.NotBlank;

public class PayerForm {

    @NotBlank(message = "Name is required")
    private String name;

    private String note;

    /** Empty form, for the create screen. */
    public PayerForm() {
    }

    /** Copies a saved payer's values in, so the edit screen renders them. */
    public static PayerForm from(Payer payer) {
        PayerForm form = new PayerForm();
        form.name = payer.getName();
        form.note = payer.getNote();
        return form;
    }

    public Payer toEntity() {
        Payer payer = new Payer(name.trim());
        applyTo(payer);
        return payer;
    }

    /** Copies this form's values onto an existing payer, for updates. */
    public void applyTo(Payer payer) {
        payer.setName(name.trim());
        payer.setNote(blankToNull(note));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
