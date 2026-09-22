package dev.bryrich.credapp.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public enum Role {
    SUPERUSER("superuser", "Superuser"),
    ADMIN("admin", "Admin"),
    COORDINATOR("coordinator", "Coordinator"),
    READONLY("readonly", "Read only");

    private final String value;
    private final String label;

    Role(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** Whether an account holding this role may act on an account holding {@code target}. */
    public boolean canManage(Role target) {
        return switch (this) {
            case SUPERUSER -> true;
            case ADMIN -> target == COORDINATOR || target == READONLY;
            case COORDINATOR, READONLY -> false;
        };
    }

    /**
     * The roles an account holding this role may hand out. An admin can promote someone up
     * to admin but cannot mint a superuser, and cannot touch one once they exist.
     */
    public List<Role> assignableRoles() {
        return switch (this) {
            case SUPERUSER -> List.of(SUPERUSER, ADMIN, COORDINATOR, READONLY);
            case ADMIN -> List.of(ADMIN, COORDINATOR, READONLY);
            case COORDINATOR, READONLY -> List.of();
        };
    }

    /**
     * Whether this role may decrypt a stored SSN. Coordinators need them to fill payer
     * forms; read-only accounts never do.
     */
    public boolean canRevealSsn() {
        return this == SUPERUSER || this == ADMIN || this == COORDINATOR;
    }

    public boolean canManageUsers() {
        return this == SUPERUSER || this == ADMIN;
    }

    public static Role fromValue(String value) {
        for (Role role : Role.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
