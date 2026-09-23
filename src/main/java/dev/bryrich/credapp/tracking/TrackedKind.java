package dev.bryrich.credapp.tracking;

/**
 * Each sort of thing that comes due. Some expire (a license lapses); others are due (a
 * reappointment or a follow-up); a stalled application is neither and is measured by how
 * long it's been waiting.
 */
public enum TrackedKind {
    LICENSE("License", TrackedCategory.LICENSES, Timing.EXPIRES, "licenses"),
    CERTIFICATION("Board certification", TrackedCategory.CERTIFICATIONS, Timing.EXPIRES, "certifications"),
    MALPRACTICE_POLICY("Malpractice policy", TrackedCategory.MALPRACTICE, Timing.EXPIRES, "policies"),
    REAPPOINTMENT("Hospital reappointment", TrackedCategory.HOSPITAL, Timing.DUE, "privileges"),
    FLU_SHOT("Flu shot", TrackedCategory.HEALTH, Timing.DUE, "details"),
    TB_TEST("TB test", TrackedCategory.HEALTH, Timing.DUE, "details"),
    CAQH_ATTESTATION("CAQH attestation", TrackedCategory.CAQH, Timing.DUE, "details"),
    RECREDENTIAL("Recredentialing", TrackedCategory.PAYERS, Timing.DUE, "payers"),
    FOLLOW_UP("Payer follow-up", TrackedCategory.PAYERS, Timing.DUE, "payers"),
    STALLED("Stalled application", TrackedCategory.PAYERS, Timing.WAITING, "payers");

    public enum Timing { EXPIRES, DUE, WAITING }

    private final String label;
    private final TrackedCategory category;
    private final Timing timing;
    /** The form section to jump to when fixing it. */
    private final String section;

    TrackedKind(String label, TrackedCategory category, Timing timing, String section) {
        this.label = label;
        this.category = category;
        this.timing = timing;
        this.section = section;
    }

    public String getLabel() {
        return label;
    }

    public TrackedCategory getCategory() {
        return category;
    }

    public Timing getTiming() {
        return timing;
    }

    public String getSection() {
        return section;
    }
}
