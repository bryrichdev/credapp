package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;

import java.util.Map;

/** One filled-in row of a template sheet, with each cell already converted. */
public record ParsedRow(Sheet sheet, int number, Map<String, Object> values) {

    public Object get(String key) {
        return values.get(key);
    }

    /** An ID or link column's value, upper-cased, or null when blank. */
    public String key(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    public boolean has(String key) {
        return values.get(key) != null;
    }

    /** How the row is named in messages: "Providers row 4". */
    public String label() {
        return sheet.name() + " row " + number;
    }
}
