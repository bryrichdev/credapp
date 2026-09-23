package dev.bryrich.credapp.provider.disclosure;

import com.fasterxml.jackson.annotation.JsonValue;

/** Where a criminal charge stands. */
public enum ChargeStatus {
    PENDING("pending"),
    CONVICTED("convicted"),
    DISMISSED("dismissed"),
    ACQUITTED("acquitted"),
    EXPUNGED("expunged");

    private final String value;

    ChargeStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ChargeStatus fromValue(String value) {
        for (ChargeStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown ChargeStatus: " + value);
    }
}
