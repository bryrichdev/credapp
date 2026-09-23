package dev.bryrich.credapp.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateProviderRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        LocalDate dob,
        String placeOfBirth,
        @Pattern(regexp = "^[0-9]{10}$") String npi,
        Sex sex,
        String phoneNumber
) {
    public Provider toEntity() {
        Provider p = new Provider(firstName, lastName);
        p.setDob(dob);
        p.setPlaceOfBirth(placeOfBirth);
        p.setNpi(npi);
        p.setSex(sex);
        p.setPhoneNumber(phoneNumber);
        return p;
    }
}
