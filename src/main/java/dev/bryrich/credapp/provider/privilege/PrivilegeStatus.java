package dev.bryrich.credapp.provider.privilege;

import com.fasterxml.jackson.annotation.JsonValue;

/** Standing of a provider's hospital privileges. */
public enum PrivilegeStatus {
    ACTIVE("active"),
    TEMPORARY("temporary"),
    COURTESY("courtesy"),
    PENDING("pending");

    private final String value;

    PrivilegeStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PrivilegeStatus fromValue(String value) {
        for (PrivilegeStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown PrivilegeStatus: " + value);
    }
}
