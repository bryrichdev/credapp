package dev.bryrich.credapp.portal.remote;

import java.util.List;
import java.util.Set;

/**
 * One thing the coordinator did on the live picture, as the page sends it. Positions are in
 * the remote page's own pixels; the page scales them from the picture's size on her screen.
 *
 * @param type      move, down, up, wheel, key (a named key or a shortcut), type (characters
 *                  she typed) or paste (text from her clipboard)
 * @param button    left, middle or right, for down and up
 * @param clicks    1 for a click, 2 for the second click of a double click
 * @param key       for key: Enter, Tab, an arrow and the like, or one character with modifiers
 * @param modifiers for key: any of Shift, Control, Alt, Meta
 */
public record RemoteInput(String type, double x, double y, String button, int clicks, double dx, double dy,
                          String key, List<String> modifiers, String text) {

    static final int MAX_TEXT = 4000;

    static final Set<String> NAMED_KEYS = Set.of("Enter", "Tab", "Backspace", "Delete", "Escape", "ArrowUp",
            "ArrowDown", "ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown");

    private static final List<String> MODIFIERS = List.of("Shift", "Control", "Alt", "Meta");

    /**
     * The key as Playwright presses it, such as "Shift+Tab" or "Control+a", or null if it
     * isn't one CredCloud passes on. Meta becomes Control: the remote browser runs on Linux,
     * so Cmd+A on a Mac should select all there too.
     */
    String chord() {
        if (key == null || !(NAMED_KEYS.contains(key) || key.codePointCount(0, key.length()) == 1)) {
            return null;
        }
        List<String> held = modifiers == null ? List.of() : modifiers;
        StringBuilder chord = new StringBuilder();
        for (String modifier : MODIFIERS) {
            if (held.contains(modifier) && !(modifier.equals("Control") && held.contains("Meta"))) {
                chord.append(modifier.equals("Meta") ? "Control" : modifier).append('+');
            }
        }
        return chord.append(key).toString();
    }

    String mouseButton() {
        return "right".equals(button) ? "right" : "middle".equals(button) ? "middle" : "left";
    }

    int clickCount() {
        return Math.clamp(clicks, 1, 3);
    }
}
