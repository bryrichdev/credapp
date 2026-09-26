package dev.bryrich.credapp.document;

import java.util.Arrays;
import java.util.List;

/** What a document is. Stored by value; the label is what people see. */
public enum DocumentType {
    STATE_LICENSE("state_license", "State license", true, false),
    DEA("dea", "DEA certificate", true, false),
    CDS("cds", "State controlled substance (CDS) registration", true, false),
    BOARD_CERTIFICATE("board_certificate", "Board certificate", true, false),
    MALPRACTICE("malpractice", "Malpractice insurance face sheet", true, true),
    CV("cv", "CV or resume", true, false),
    DIPLOMA("diploma", "Diploma", true, false),
    TRAINING_CERTIFICATE("training_certificate", "Training certificate", true, false),
    PHOTO_ID("photo_id", "Photo ID", true, false),
    IMMUNIZATION("immunization", "Immunization or TB record", true, false),
    W9("w9", "W-9", true, true),
    IRS_LETTER("irs_letter", "IRS letter (CP 575 or 147C)", false, true),
    BUSINESS_LICENSE("business_license", "Business license", false, true),
    CLIA("clia", "CLIA certificate", false, true),
    OTHER("other", "Other", true, true);

    private final String value;
    private final String label;
    private final boolean forProviders;
    private final boolean forGroups;

    DocumentType(String value, String label, boolean forProviders, boolean forGroups) {
        this.value = value;
        this.label = label;
        this.forProviders = forProviders;
        this.forGroups = forGroups;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static List<DocumentType> forProviders() {
        return Arrays.stream(values()).filter(type -> type.forProviders).toList();
    }

    public static List<DocumentType> forGroups() {
        return Arrays.stream(values()).filter(type -> type.forGroups).toList();
    }

    public static DocumentType fromValue(String value) {
        for (DocumentType type : values()) {
            if (type.value.equals(value) || type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Choose what kind of document this is");
    }
}
