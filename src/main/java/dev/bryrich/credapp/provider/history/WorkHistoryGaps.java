package dev.bryrich.credapp.provider.history;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Stretches of six months or more that nothing covers: no job, no explained time away, and no
 * training. Applications ask for each of these to be explained, so the provider page lists them.
 * Counting starts at the earliest entry; time before a provider's first job isn't a gap.
 */
public final class WorkHistoryGaps {

    /** CAQH asks about gaps of six months or more. */
    static final long MIN_DAYS = 183;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);

    /** A gap, and how long it is. {@code to} is today when the gap is still going. */
    public record Gap(LocalDate from, LocalDate to, boolean ongoing) {
        public String describe() {
            long months = ChronoUnit.MONTHS.between(from, to);
            return MONTH.format(from) + " to " + (ongoing ? "now" : MONTH.format(to))
                    + " (" + (months == 1 ? "1 month" : months + " months") + ")";
        }
    }

    private record Span(LocalDate start, LocalDate end) {
    }

    private WorkHistoryGaps() {
    }

    public static List<Gap> find(List<WorkHistoryEntry> work, List<ProviderTraining> training, LocalDate today) {
        List<Span> spans = new ArrayList<>();
        work.forEach(entry -> spans.add(new Span(entry.getStartDate(), entry.getEndDate() == null ? today : entry.getEndDate())));
        training.forEach(entry -> spans.add(new Span(entry.getStartDate(), entry.getEndDate() == null ? today : entry.getEndDate())));
        if (spans.isEmpty()) {
            return List.of();
        }
        spans.sort(Comparator.comparing(Span::start));

        List<Gap> gaps = new ArrayList<>();
        LocalDate coveredTo = spans.getFirst().end();
        for (Span span : spans.subList(1, spans.size())) {
            if (ChronoUnit.DAYS.between(coveredTo, span.start()) >= MIN_DAYS) {
                gaps.add(new Gap(coveredTo, span.start(), false));
            }
            if (span.end().isAfter(coveredTo)) {
                coveredTo = span.end();
            }
        }
        if (ChronoUnit.DAYS.between(coveredTo, today) >= MIN_DAYS) {
            gaps.add(new Gap(coveredTo, today, true));
        }
        return gaps;
    }
}
