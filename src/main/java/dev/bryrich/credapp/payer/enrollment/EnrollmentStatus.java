package dev.bryrich.credapp.payer.enrollment;

import com.fasterxml.jackson.annotation.JsonValue;

/** Where a provider's or group's enrollment with a payer stands. */
public enum EnrollmentStatus {
    NOT_STARTED("not_started", "Not started"),
    IN_PROGRESS("in_progress", "In progress"),
    SUBMITTED("submitted", "Submitted"),
    ACTIVE("active", "Active"),
    DENIED("denied", "Denied"),
    TERMINATED("terminated", "Terminated");

    private final String value;
    private final String label;

    EnrollmentStatus(String value, String label) {
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

    /** The badge style pages show the status in. */
    public String getBadge() {
        return switch (this) {
            case ACTIVE -> "badge--active";
            case IN_PROGRESS, SUBMITTED -> "badge--pending";
            case DENIED, TERMINATED -> "badge--alert";
            case NOT_STARTED -> "badge--inactive";
        };
    }

    public static EnrollmentStatus fromValue(String value) {
        for (EnrollmentStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown EnrollmentStatus: " + value);
    }
}
