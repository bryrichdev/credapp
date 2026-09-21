package dev.bryrich.credapp.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    ADMIN("admin"),
    COORDINATOR("coordinator"),
    READONLY("readonly");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
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
