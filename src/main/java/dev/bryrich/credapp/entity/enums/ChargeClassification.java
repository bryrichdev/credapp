package dev.bryrich.credapp.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** Severity tier of a criminal charge. */
public enum ChargeClassification {
    FELONY("felony"),
    MISDEMEANOR("misdemeanor");

    private final String value;

    ChargeClassification(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ChargeClassification fromValue(String value) {
        for (ChargeClassification item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown ChargeClassification: " + value);
    }
}
