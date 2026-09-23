package dev.bryrich.credapp.owner;

public enum Relationship {
    SPOUSE("spouse"),
    SIBLING("sibling"),
    PARENT("parent"),
    CHILD("child");

    private final String dbValue;

    Relationship(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public Relationship inverse() {
        return switch (this) {
            case PARENT -> CHILD;
            case CHILD -> PARENT;
            case SPOUSE, SIBLING -> this;
        };
    }

    public static Relationship fromDb(String value) {
        for (Relationship r : values()) {
            if (r.dbValue.equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown relationship: " + value);
    }
}
