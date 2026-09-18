package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min=12) String password,
        String fullName,
        Role role
) {
}
