package dev.bryrich.credapp.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Sex {
    MALE("M", "Male"),
    FEMALE("F", "Female"),
    OTHER("X", "Other"),
    UNKNOWN("U", "Unknown");

    private final String value;
    private final String label;

    Sex(String value, String label) {
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

    public static Sex fromValue(String value) {
        for (Sex sex : values()) {
            if (sex.value.equals(value)) {
                return sex;
            }
        }
        throw new IllegalArgumentException("Unknown sex: " + value);
    }
}
