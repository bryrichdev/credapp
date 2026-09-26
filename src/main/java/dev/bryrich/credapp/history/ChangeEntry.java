package dev.bryrich.credapp.history;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One line of a provider's or group's history: "Jane Smith changed License UT 12345".
 *
 * @param actor   who did it, or null when no one was signed in (a background job)
 * @param section what kind of record, such as "License"
 * @param item    which one, such as "UT 12345"; null when there's nothing more specific
 * @param fields  for a change, what changed; empty for an add or removal
 */
public record ChangeEntry(long id, Instant at, String actor, Action action, String section, String item,
                          List<FieldChange> fields) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a z", Locale.US);

    /** "Sep 26, 2026 at 3:04 PM EDT" in the server's zone; the page rewrites it in the viewer's own. */
    public String when() {
        return WHEN.format(at.atZone(ZoneId.systemDefault()));
    }

    public enum Action {
        ADDED("Added"), CHANGED("Changed"), REMOVED("Removed");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        static Action of(String operation) {
            return switch (operation) {
                case "I" -> ADDED;
                case "U" -> CHANGED;
                case "D" -> REMOVED;
                default -> throw new IllegalArgumentException(operation);
            };
        }
    }

    /** A changed field. A masked one (an SSN, a password) says it changed but never shows values. */
    public record FieldChange(String label, String from, String to, boolean masked) {
    }
}
