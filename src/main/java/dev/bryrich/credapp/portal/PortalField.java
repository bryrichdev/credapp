package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.application.AnswerFormat;

import java.util.List;
import java.util.Set;

/**
 * One box on a payer portal and the answer it gets. The runner records these in learn mode.
 *
 * @param label        what the box is called on the page, for people and for fill reports
 * @param by           how the runner finds the box: {@code label} (its visible label text, which
 *                     survives most redesigns) or {@code css} (a selector, the fallback)
 * @param locator      the label text or the selector
 * @param kind         {@code text}, {@code select}, {@code checkbox} or {@code radio}; the runner
 *                     types, picks an option or ticks a box. It never clicks anything else.
 * @param source       an {@link dev.bryrich.credapp.application.ApplicationDataService} key, or
 *                     empty to always use {@code defaultValue}
 * @param format       an {@link AnswerFormat} name, or empty for as saved
 * @param defaultValue used when there's no source, or the source is empty
 * @param page         the path of the page the box is on, so a fill only looks for boxes there
 */
public record PortalField(String label, String by, String locator, String kind, String source,
                          String format, String defaultValue, String page) {

    public static final int MAX_FIELDS = 250;
    private static final Set<String> BY = Set.of("label", "css");
    private static final Set<String> KINDS = Set.of("text", "select", "checkbox", "radio");

    public PortalField {
        label = clean(label);
        by = clean(by);
        locator = locator == null ? "" : locator.trim();
        kind = clean(kind);
        source = clean(source);
        format = clean(format);
        defaultValue = defaultValue == null ? "" : defaultValue.trim();
        page = clean(page);
    }

    public AnswerFormat answerFormat() {
        return AnswerFormat.of(format);
    }

    /**
     * Checks a set of fields as the runner sends them.
     *
     * @throws IllegalArgumentException with a message for the coordinator
     */
    public static void check(List<PortalField> fields, Set<String> sources) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Show the runner at least one box before saving");
        }
        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("A portal template can have up to " + MAX_FIELDS + " boxes");
        }
        for (PortalField field : fields) {
            String name = field.label().isEmpty() ? "a box" : "\"" + field.label() + "\"";
            if (field.label().length() > 200) {
                throw new IllegalArgumentException("The name of " + name + " is too long");
            }
            if (!BY.contains(field.by()) || field.locator().isEmpty() || field.locator().length() > 500) {
                throw new IllegalArgumentException("The runner couldn't describe where " + name + " is. Show it again.");
            }
            if (!KINDS.contains(field.kind())) {
                throw new IllegalArgumentException(name + " isn't a box the runner can fill");
            }
            if (!field.source().isEmpty() && !sources.contains(field.source())) {
                throw new IllegalArgumentException("Choose listed data for " + name);
            }
            if (field.source().isEmpty() && field.defaultValue().isEmpty()) {
                throw new IllegalArgumentException("Choose data or a fixed answer for " + name);
            }
            if (field.defaultValue().length() > 500 || field.page().length() > 500) {
                throw new IllegalArgumentException("The answer for " + name + " is too long");
            }
            try {
                AnswerFormat.of(field.format());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Choose a listed format for " + name);
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
