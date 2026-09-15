package dev.bryrich.credapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateLicenseRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String state,
        @NotBlank String licenseNumber,
        @NotBlank String licenseType,
        LocalDate issueDate,
        @NotNull LocalDate expirationDate,
        @NotBlank String status,
        String restrictions
) {}