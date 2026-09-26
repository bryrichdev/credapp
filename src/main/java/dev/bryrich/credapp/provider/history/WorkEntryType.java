package dev.bryrich.credapp.provider.history;

/** A work history entry is a job or explained time away from work. */
public enum WorkEntryType {
    JOB("Job"),
    GAP("Time away");

    private final String label;

    WorkEntryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
