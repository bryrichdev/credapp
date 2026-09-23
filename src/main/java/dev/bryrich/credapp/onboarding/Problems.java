package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects problems across every step of an import. A cell gets one message: the first one
 * found wins, so a blank required cell isn't reported again by the form's own validation.
 */
public class Problems {

    private final List<ImportProblem> problems = new ArrayList<>();
    private final Set<String> cellsReported = new HashSet<>();

    public void file(String message) {
        problems.add(new ImportProblem(null, null, null, message));
    }

    public void sheet(String sheet, String message) {
        problems.add(new ImportProblem(sheet, null, null, message));
    }

    /** column is a header, or null for the row as a whole. */
    public void cell(String sheet, int row, String column, String message) {
        if (column != null && !cellsReported.add(sheet + "\u0000" + row + "\u0000" + column)) {
            return;
        }
        ImportProblem problem = new ImportProblem(sheet, row, column, message);
        if (column == null && problems.contains(problem)) {
            return;
        }
        problems.add(problem);
    }

    /** key is a column key; the message is attached to that column's header. */
    public void at(ParsedRow row, String key, String message) {
        cell(row.sheet().name(), row.number(), key == null ? null : row.sheet().headerFor(key), message);
    }

    public boolean isEmpty() {
        return problems.isEmpty();
    }

    public int size() {
        return problems.size();
    }

    /** File problems first, then by sheet in template order, then by row. */
    public List<ImportProblem> sorted() {
        List<String> order = OnboardingTemplate.SHEETS.stream().map(Sheet::name).toList();
        return problems.stream()
                .sorted(Comparator
                        .comparingInt((ImportProblem p) -> p.sheet() == null ? -1
                                : order.indexOf(p.sheet()) < 0 ? order.size() : order.indexOf(p.sheet()))
                        .thenComparingInt(p -> p.row() == null ? 0 : p.row()))
                .toList();
    }
}
