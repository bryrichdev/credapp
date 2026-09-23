package dev.bryrich.credapp.tracking;

import java.util.function.LongSupplier;

/**
 * The count on the Tracking link: how many things need doing now. Worked out only when a
 * page actually shows the top bar, and at most once per request, since it reads a dozen
 * tables and every request (redirects and JSON included) gets the page model.
 */
public final class TrackingBadge {

    public static final TrackingBadge NONE = new TrackingBadge(() -> 0);

    private final LongSupplier source;
    private Long count;

    public TrackingBadge(LongSupplier source) {
        this.source = source;
    }

    public long getCount() {
        if (count == null) {
            count = source.getAsLong();
        }
        return count;
    }
}
