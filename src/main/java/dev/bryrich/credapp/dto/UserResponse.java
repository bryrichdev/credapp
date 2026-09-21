package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.enums.Role;
import dev.bryrich.credapp.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean enabled,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
