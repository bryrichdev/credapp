package dev.bryrich.credapp.tracking;

import java.time.LocalDate;

/**
 * One thing that's expired, coming due or waiting too long, and whose it is.
 *
 * @param date     when it expires or is due; for a stalled application, when it started waiting
 * @param days     days until the date (negative once past); for a stalled application, days waiting
 * @param what     what it is, such as "OH MD license 35.071234" or "Aetna"
 * @param payerId  the payer, for payer items; null otherwise
 */
public record TrackedItem(TrackedKind kind, TrackedState state, LocalDate date, long days, String what,
                          Subject subject, Long payerId) {

    public enum SubjectType { PROVIDER, GROUP }

    /** The provider or group it belongs to. */
    public record Subject(SubjectType type, Long id, String name) {
        public String path() {
            return (type == SubjectType.PROVIDER ? "/providers/" : "/groups/") + id;
        }
    }

    /** "Expired", "Overdue", "Due soon"... in the words that fit the kind. */
    public String stateLabel() {
        if (state == TrackedState.OVERDUE && kind.getTiming() == TrackedKind.Timing.EXPIRES) {
            return "Expired";
        }
        return state.getLabel();
    }

    /** "in 12 days", "today", "5 days ago", or "waiting 75 days". */
    public String when() {
        if (state == TrackedState.STALLED) {
            return "waiting " + plural(days, "day");
        }
        if (days == 0) {
            return "today";
        }
        return days > 0 ? "in " + plural(days, "day") : plural(-days, "day") + " ago";
    }

    /** The form section where it's fixed. */
    public String editPath() {
        if (kind == TrackedKind.DOCUMENT) {
            // Documents are managed on the provider's or group's own page, not the edit form.
            return subject.path() + "#documents";
        }
        return subject.path() + "/edit#" + kind.getSection();
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
