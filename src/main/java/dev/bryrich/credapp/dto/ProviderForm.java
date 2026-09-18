package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Provider;
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

    private String sex;

    private String phoneNumber;

    public Provider toEntity() {
        Provider provider = new Provider(firstName, lastName);
        provider.setDob(dob);
        provider.setPlaceOfBirth(placeOfBirth);
        provider.setNpi(npi == null || npi.isBlank() ? null : npi);
        provider.setSex(sex);
        provider.setPhoneNumber(phoneNumber);
        return provider;
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

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}