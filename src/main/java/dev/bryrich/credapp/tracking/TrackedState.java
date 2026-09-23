package dev.bryrich.credapp.tracking;

/** How pressing an item is, most pressing first. */
public enum TrackedState {
    OVERDUE("Overdue", "badge--alert"),
    DUE_SOON("Due soon", "badge--pending"),
    STALLED("Stalled", "badge--pending"),
    COMING_UP("Coming up", "badge--inactive");

    private final String label;
    private final String badge;

    TrackedState(String label, String badge) {
        this.label = label;
        this.badge = badge;
    }

    public String getLabel() {
        return label;
    }

    public String getBadge() {
        return badge;
    }

    /** Needs doing now rather than just knowing about: counted on the nav. */
    public boolean needsAction() {
        return this != COMING_UP;
    }
}
