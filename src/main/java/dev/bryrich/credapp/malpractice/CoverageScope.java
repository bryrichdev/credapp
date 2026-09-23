package dev.bryrich.credapp.malpractice;

import com.fasterxml.jackson.annotation.JsonValue;

/** Whether a malpractice policy is shared or individual. */
public enum CoverageScope {
    SHARED("shared"),
    INDIVIDUAL("individual");

    private final String value;

    CoverageScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static CoverageScope fromValue(String value) {
        for (CoverageScope item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown CoverageScope: " + value);
    }
}
