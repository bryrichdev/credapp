package dev.bryrich.credapp.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a provider serves a location; provider_locations.pcp_scp. */
public enum PcpScp {
    PCP("pcp"),
    SCP("scp");

    private final String value;

    PcpScp(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PcpScp fromValue(String value) {
        for (PcpScp item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown PcpScp: " + value);
    }
}
