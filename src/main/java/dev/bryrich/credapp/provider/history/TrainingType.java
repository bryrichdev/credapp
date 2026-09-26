package dev.bryrich.credapp.provider.history;

/** Kinds of postgraduate training, as CAQH lists them. */
public enum TrainingType {
    INTERNSHIP("Internship"),
    RESIDENCY("Residency"),
    FELLOWSHIP("Fellowship"),
    OTHER("Other training");

    private final String label;

    TrainingType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
