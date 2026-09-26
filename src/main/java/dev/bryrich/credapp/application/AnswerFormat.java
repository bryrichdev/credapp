package dev.bryrich.credapp.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * How a saved value is written into one PDF field. Data is stored one way (dates as
 * 2026-01-31, SSNs as nine digits); each form wants its own. A value that doesn't fit the
 * format, such as a date field holding text, is left as it is for review.
 */
public enum AnswerFormat {
    AS_SAVED("As saved", value -> value),
    DATE_MDY("Date MM/DD/YYYY", value -> date(value, "MM/dd/yyyy")),
    DATE_MDY_DASH("Date MM-DD-YYYY", value -> date(value, "MM-dd-yyyy")),
    DATE_MY("Date MM/YYYY", value -> date(value, "MM/yyyy")),
    DATE_LONG("Date January 31, 2026", value -> date(value, "MMMM d, yyyy")),
    DIGITS("Digits only", value -> value.replaceAll("\\D", "")),
    SSN("SSN 123-45-6789", value -> grouped(value, 9, "$1-$2-$3", "(\\d{3})(\\d{2})(\\d{4})")),
    TAX_ID("Tax ID 12-3456789", value -> grouped(value, 9, "$1-$2", "(\\d{2})(\\d{7})")),
    PHONE_DASHES("Phone 801-555-0142", value -> grouped(value, 10, "$1-$2-$3", "(\\d{3})(\\d{3})(\\d{4})")),
    PHONE_PARENS("Phone (801) 555-0142", value -> grouped(value, 10, "($1) $2-$3", "(\\d{3})(\\d{3})(\\d{4})")),
    UPPER("UPPERCASE", value -> value.toUpperCase(Locale.ROOT));

    private final String label;
    private final UnaryOperator<String> apply;

    AnswerFormat(String label, UnaryOperator<String> apply) {
        this.label = label;
        this.apply = apply;
    }

    public String getLabel() {
        return label;
    }

    public String apply(String value) {
        return value == null || value.isBlank() ? "" : apply.apply(value.trim());
    }

    /** A stored format name, with older mappings (saved before formats existed) as-saved. */
    public static AnswerFormat of(String name) {
        if (name == null || name.isBlank()) {
            return AS_SAVED;
        }
        return valueOf(name);
    }

    private static String date(String value, String pattern) {
        try {
            return LocalDate.parse(value).format(DateTimeFormatter.ofPattern(pattern, Locale.US));
        } catch (DateTimeParseException ex) {
            return value;
        }
    }

    /** Groups a number's digits, when it has exactly the expected count. */
    private static String grouped(String value, int count, String replacement, String pattern) {
        String digits = value.replaceAll("\\D", "");
        return digits.length() == count ? digits.replaceFirst(pattern, replacement) : value;
    }
}
