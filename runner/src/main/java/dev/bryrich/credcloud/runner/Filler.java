package dev.bryrich.credcloud.runner;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.SelectOption;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Types answers into the boxes on a portal page. It can type into a text box, pick a dropdown
 * option and tick a checkbox or radio button. It checks each element before touching it and
 * leaves anything else alone, so a template that points at a button, or a page that changed,
 * can't make it press submit.
 */
final class Filler {

    /** What one press of "Fill this page" did. */
    record Report(List<Integer> filled, List<String> problems) {
    }

    private static final Set<String> YES = Set.of("yes", "y", "true", "1", "x", "checked", "on");
    private static final double TIMEOUT_MS = 3000;

    /** What the element really is. Anything that isn't a box comes back as "forbidden". */
    private static final String ELEMENT_KIND = """
            e => {
              const tag = e.tagName.toLowerCase();
              const type = (e.getAttribute('type') || 'text').toLowerCase();
              if (tag === 'select') return 'select';
              if (tag === 'textarea') return 'text';
              if (tag === 'input') {
                if (['submit', 'button', 'image', 'reset', 'file', 'hidden'].includes(type)) return 'forbidden';
                if (type === 'checkbox' || type === 'radio') return type;
                return 'text';
              }
              if (e.isContentEditable) return 'text';
              const role = e.getAttribute('role');
              if (role === 'checkbox' || role === 'radio') return role;
              return 'forbidden';
            }""";

    private Filler() {
    }

    /** The portal a job was sent to, and pages on the same site (such as its sign-in). */
    static boolean samePortal(String pageUrl, String startUrl) {
        try {
            String page = URI.create(pageUrl).getHost();
            String start = URI.create(startUrl).getHost();
            return page != null && start != null && site(page).equals(site(start));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The last two labels of a host: portal.payer.com and login.payer.com are one site. */
    private static String site(String host) {
        String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
        return labels.length < 2 ? host : labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    static Locator locate(Page page, Model.Field field) {
        return "label".equals(field.by())
                ? page.getByLabel(field.locator(), new Page.GetByLabelOptions().setExact(true))
                : page.locator(field.locator());
    }

    /**
     * Fills every box of the template that's on this page and has an answer.
     *
     * @param skip fields filled on an earlier page, left alone
     */
    static Report fill(Page page, List<Model.Field> fields, List<String> answers, Set<Integer> skip) {
        List<Integer> filled = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Model.Field field = fields.get(i);
            String value = i < answers.size() ? answers.get(i) : "";
            if (skip.contains(i) || value == null || value.isBlank()) {
                continue;
            }
            try {
                Locator found = locate(page, field);
                if (found.count() == 0) {
                    continue; // on another page of the form
                }
                Locator box = found.first();
                String actual = (String) box.evaluate(ELEMENT_KIND);
                if (!actual.equals(field.kind())) {
                    problems.add(field.label() + ": the page has " + ("forbidden".equals(actual)
                            ? "a button or something else that isn't a box" : "a " + actual) + " there now");
                    continue;
                }
                switch (actual) {
                    case "text" -> box.fill(value, new Locator.FillOptions().setTimeout(TIMEOUT_MS));
                    case "select" -> select(box, value);
                    case "checkbox" -> box.setChecked(yes(value), new Locator.SetCheckedOptions().setTimeout(TIMEOUT_MS));
                    case "radio" -> {
                        if (yes(value)) {
                            box.check(new Locator.CheckOptions().setTimeout(TIMEOUT_MS));
                        }
                    }
                    default -> throw new IllegalStateException(actual);
                }
                box.evaluate("e => { e.style.outline = '2px solid #2f855a'; e.style.outlineOffset = '1px'; }");
                filled.add(i);
            } catch (PlaywrightException e) {
                problems.add(field.label() + ": " + firstLine(e.getMessage()));
            }
        }
        return new Report(filled, problems);
    }

    private static void select(Locator box, String value) {
        var options = new Locator.SelectOptionOptions().setTimeout(TIMEOUT_MS);
        try {
            box.selectOption(new SelectOption().setLabel(value), options);
        } catch (PlaywrightException byLabel) {
            box.selectOption(new SelectOption().setValue(value), options);
        }
    }

    static boolean yes(String value) {
        return value != null && YES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static String firstLine(String message) {
        String text = message == null ? "couldn't fill it" : message.strip();
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
