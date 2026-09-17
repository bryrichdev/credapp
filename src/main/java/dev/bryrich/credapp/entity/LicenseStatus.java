package dev.bryrich.credapp.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LicenseStatus {
    ACTIVE("active"),
    EXPIRED("expired"),
    SUSPENDED("suspended"),
    REVOKED("revoked"),
    SURRENDERED("surrendered"),
    PROBATION("probation"),
    INACTIVE("inactive"),
    PENDING("pending");

    private final String value;

    LicenseStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static LicenseStatus fromValue(String value) {
        for (LicenseStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown license status: " + value);
    }
}