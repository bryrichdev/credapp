package dev.bryrich.credapp.ssn;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SsnSubjectType {
    OWNER("owner"),
    PROVIDER("provider");

    private final String value;

    SsnSubjectType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static SsnSubjectType fromValue(String value) {
        for (SsnSubjectType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SSN subject type: " + value);
    }
}
