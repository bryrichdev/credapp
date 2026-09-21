package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.Sex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ProviderForm {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dob;

    private String placeOfBirth;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "NPI must be exactly 10 digits")
    private String npi;

    private Sex sex;

    private String phoneNumber;

    /** Empty form, for the create screen. */
    public ProviderForm() {
    }

    /** Copies a saved provider's values in, so the edit screen renders them. */
    public static ProviderForm from(Provider provider) {
        ProviderForm form = new ProviderForm();
        form.firstName = provider.getFirstName();
        form.lastName = provider.getLastName();
        form.dob = provider.getDob();
        form.placeOfBirth = provider.getPlaceOfBirth();
        form.npi = provider.getNpi();
        form.sex = provider.getSex();
        form.phoneNumber = provider.getPhoneNumber();
        return form;
    }

    public Provider toEntity() {
        Provider provider = new Provider(firstName, lastName);
        applyTo(provider);
        return provider;
    }

    /** Copies this form's values onto an existing provider, for updates. */
    public void applyTo(Provider provider) {
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setDob(dob);
        provider.setPlaceOfBirth(blankToNull(placeOfBirth));
        provider.setNpi(blankToNull(npi));
        provider.setSex(sex);
        provider.setPhoneNumber(blankToNull(phoneNumber));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
