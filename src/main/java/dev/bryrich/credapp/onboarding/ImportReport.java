package dev.bryrich.credapp.onboarding;

import java.util.List;

/**
 * What an upload would do (a preview) or did (an import). saved is true only when an
 * import committed. counts lists the rows read from each sheet that had any.
 */
public record ImportReport(boolean saved, List<ImportProblem> problems, List<SheetCount> counts,
                           List<String> groups, List<String> providers, List<String> payers) {

    public record SheetCount(String sheet, int rows) {
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public int totalRows() {
        return counts.stream().mapToInt(SheetCount::rows).sum();
    }
}
