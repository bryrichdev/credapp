package dev.bryrich.credapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateProviderRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        LocalDate dob,
        String placeOfBirth,
        @Pattern(regexp = "^[0-9]{10}$") String npi,
        String sex,
        String phoneNumber
) {}
