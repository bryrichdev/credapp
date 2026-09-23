package dev.bryrich.credapp.provider.reference;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

public class ProviderReferenceForm {

    @NotBlank(message = "Name is required")
    private String name;

    private String title;

    @NotBlank(message = "Relationship is required")
    private String relationship;

    @Email(message = "Enter a valid email address")
    private String emailAddress;

    private String street1;
    private String street2;
    private String city;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;

    @Pattern(regexp = "^$|^[0-9]{5}(-[0-9]{4})?$", message = "ZIP must be 5 or 9 digits")
    private String zipCode;

    private String phoneNumber;

    /** Empty form, for the create screen. */
    public ProviderReferenceForm() {
    }

    /** Copies a saved reference's values in, so the edit screen renders them. */
    public static ProviderReferenceForm from(ProviderReference reference) {
        ProviderReferenceForm form = new ProviderReferenceForm();
        form.name = reference.getName();
        form.title = reference.getTitle();
        form.relationship = reference.getRelationship();
        form.emailAddress = reference.getEmailAddress();
        form.street1 = reference.getStreet1();
        form.street2 = reference.getStreet2();
        form.city = reference.getCity();
        form.state = reference.getState();
        form.zipCode = reference.getZipCode();
        form.phoneNumber = reference.getPhoneNumber();
        return form;
    }

    public ProviderReference toEntity() {
        ProviderReference reference = new ProviderReference(name, relationship);
        applyTo(reference);
        return reference;
    }

    /** Copies this form's values onto an existing reference, for updates. */
    public void applyTo(ProviderReference reference) {
        reference.setName(name);
        reference.setTitle(blankToNull(title));
        reference.setRelationship(relationship);
        reference.setEmailAddress(blankToNull(emailAddress));
        reference.setStreet1(blankToNull(street1));
        reference.setStreet2(blankToNull(street2));
        reference.setCity(blankToNull(city));
        reference.setState(upperOrNull(state));
        reference.setZipCode(blankToNull(zipCode));
        reference.setPhoneNumber(blankToNull(phoneNumber));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
