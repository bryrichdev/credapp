package dev.bryrich.credapp.tracking;

/** How the tracking page groups what it lists; also the filter's choices. */
public enum TrackedCategory {
    LICENSES("Licenses"),
    CERTIFICATIONS("Board certifications"),
    MALPRACTICE("Malpractice"),
    HOSPITAL("Hospital reappointment"),
    HEALTH("Flu shots & TB tests"),
    CAQH("CAQH attestation"),
    PAYERS("Payers");

    private final String label;

    TrackedCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
