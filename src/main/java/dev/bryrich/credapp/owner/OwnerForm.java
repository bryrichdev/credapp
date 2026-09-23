package dev.bryrich.credapp.owner;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
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

    private String street1;
    private String street2;
    private String city;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;

    @Pattern(regexp = "^$|^[0-9]{5}(-[0-9]{4})?$", message = "ZIP must be 5 or 9 digits")
    private String zipCode;

    /**
     * Optional, and only read when creating. The stake lives in group_owners, so applyTo
     * leaves both alone and the controller records them after the owner is saved.
     */
    private Long groupId;

    @DecimalMin(value = "0.01", message = "Percent owned must be greater than 0")
    @DecimalMax(value = "100.00", message = "Percent owned cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Percent owned allows at most two decimal places")
    private BigDecimal percentOwned;

    /** Empty form, for the create screen. */
    public OwnerForm() {
    }

    /** Copies a saved owner's values in, apart from the SSN. */
    public static OwnerForm from(Owner owner) {
        OwnerForm form = new OwnerForm();
        form.firstName = owner.getFirstName();
        form.lastName = owner.getLastName();
        form.dob = owner.getDob();
        form.street1 = owner.getStreet1();
        form.street2 = owner.getStreet2();
        form.city = owner.getCity();
        form.state = owner.getState();
        form.zipCode = owner.getZipCode();
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
        owner.setStreet1(blankToNull(street1));
        owner.setStreet2(blankToNull(street2));
        owner.setCity(blankToNull(city));
        owner.setState(upperOrNull(state));
        owner.setZipCode(blankToNull(zipCode));
        if (blankToNull(ssn) != null) {
            owner.setSsn(ssn);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public BigDecimal getPercentOwned() {
        return percentOwned;
    }

    public void setPercentOwned(BigDecimal percentOwned) {
        this.percentOwned = percentOwned;
    }
}
