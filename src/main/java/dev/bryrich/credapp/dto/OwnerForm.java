package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Owner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * The SSN is never read back into the form. SsnConverter can decrypt it, but rendering it
 * into a page would put it in the browser and in any cached HTML. A blank SSN on save
 * therefore means "leave whatever is stored alone", not "clear it".
 */
public class OwnerForm {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dob;

    @Pattern(regexp = "^$|^[0-9]{9}$", message = "SSN must be exactly 9 digits")
    private String ssn;

    private String homeAddress;

    /** Empty form, for the create screen. */
    public OwnerForm() {
    }

    /** Copies a saved owner's values in, apart from the SSN. */
    public static OwnerForm from(Owner owner) {
        OwnerForm form = new OwnerForm();
        form.firstName = owner.getFirstName();
        form.lastName = owner.getLastName();
        form.dob = owner.getDob();
        form.homeAddress = owner.getHomeAddress();
        return form;
    }

    public Owner toEntity() {
        Owner owner = new Owner(firstName, lastName);
        applyTo(owner);
        return owner;
    }

    /** Copies this form's values onto an existing owner, for updates. */
    public void applyTo(Owner owner) {
        owner.setFirstName(firstName);
        owner.setLastName(lastName);
        owner.setDob(dob);
        owner.setHomeAddress(blankToNull(homeAddress));
        if (blankToNull(ssn) != null) {
            owner.setSsn(ssn);
        }
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

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }
}
