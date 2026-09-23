package dev.bryrich.credapp.onboarding;

/**
 * Something that stops the import, pinned as closely as possible: a whole file (sheet
 * null), a sheet (row null), a row (column null) or one cell.
 */
public record ImportProblem(String sheet, Integer row, String column, String message) {
}
