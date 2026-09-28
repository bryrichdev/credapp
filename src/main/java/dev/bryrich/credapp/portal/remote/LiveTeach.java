package dev.bryrich.credapp.portal.remote;

import com.google.gson.JsonObject;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import dev.bryrich.credapp.portal.PortalField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teaching CredCloud a portal in its own browser: she picks a box in the live picture, says
 * what goes in it, and saves the boxes as the template's next version. It starts from the
 * template's current boxes, so teaching again only needs the ones that changed.
 */
public final class LiveTeach {

    /** A box she clicked, before she says what goes in it. */
    public record Picked(String label, String by, String locator, String kind, String page) {
    }

    /** What a click in pick mode found: a box, or why not. */
    public record Pick(Picked box, String problem) {
    }

    /** A taught box as the page lists it. */
    public record Row(String label, String detail) {
    }

    private static final int MAX_FRAME_DEPTH = 5;

    private final long templateId;
    private final String startUrl;
    private final Map<String, String> sources;
    private final List<PortalField> fields;
    private boolean unsaved;

    /**
     * @param sources the data a box can take, key to label, in the order to offer them
     */
    public LiveTeach(long templateId, String startUrl, List<PortalField> current, Map<String, String> sources) {
        this.templateId = templateId;
        this.startUrl = startUrl;
        this.fields = new ArrayList<>(current);
        this.sources = new LinkedHashMap<>(sources);
    }

    public long templateId() {
        return templateId;
    }

    /** The box at a point of the picture, looking inside the portal's own frames. Runs on the session's thread. */
    public Pick pickAt(Page page, double x, double y) {
        if (!PortalScript.samePortal(page.url(), startUrl)) {
            return problem("This page isn't on the portal you're teaching. Go back to it, then pick again.");
        }
        Frame frame = page.mainFrame();
        double fx = x;
        double fy = y;
        for (int depth = 0; depth < MAX_FRAME_DEPTH; depth++) {
            JsonObject found = PortalScript.run(frame, Map.of("action", "pick", "x", fx, "y", fy));
            switch (found.get("result").getAsString()) {
                case "box" -> {
                    String label = found.get("label").getAsString();
                    return new Pick(new Picked(label.isEmpty() ? "Box " + (count() + 1) : label,
                            found.get("by").getAsString(), found.get("locator").getAsString(),
                            found.get("kind").getAsString(), PortalScript.path(page.url())), null);
                }
                case "unclear" -> {
                    return problem("CredCloud couldn't pin that box down. Try clicking its label instead.");
                }
                case "frame" -> {
                    List<ElementHandle> frames = frame.querySelectorAll("iframe, frame");
                    int index = found.get("index").getAsInt();
                    Frame child = index >= 0 && index < frames.size() ? frames.get(index).contentFrame() : null;
                    if (child == null) {
                        return notABox();
                    }
                    if (!PortalScript.samePortal(child.url(), startUrl)) {
                        return problem("That box is inside another website's frame. CredCloud only fills the "
                                + "portal's own pages.");
                    }
                    fx -= found.get("left").getAsDouble();
                    fy -= found.get("top").getAsDouble();
                    frame = child;
                }
                default -> {
                    return notABox();
                }
            }
        }
        return notABox();
    }

    /**
     * Adds a box with what goes in it.
     *
     * @throws IllegalArgumentException with a message for her, if it isn't complete
     */
    public synchronized void add(PortalField field) {
        if (fields.size() >= PortalField.MAX_FIELDS) {
            throw new IllegalArgumentException("A portal template can have up to " + PortalField.MAX_FIELDS + " boxes");
        }
        PortalField.check(List.of(field), sources.keySet());
        fields.add(field);
        unsaved = true;
    }

    public synchronized void remove(int index) {
        if (index >= 0 && index < fields.size()) {
            fields.remove(index);
            unsaved = true;
        }
    }

    public synchronized List<PortalField> fields() {
        return List.copyOf(fields);
    }

    public synchronized int count() {
        return fields.size();
    }

    public synchronized boolean unsaved() {
        return unsaved;
    }

    public synchronized void saved() {
        unsaved = false;
    }

    public synchronized List<Row> rows() {
        return fields.stream().map(field -> new Row(field.label(), detail(field))).toList();
    }

    private String detail(PortalField field) {
        if (field.source().isEmpty()) {
            return "always \"" + field.defaultValue() + "\"";
        }
        String detail = sources.getOrDefault(field.source(), field.source());
        String format = field.answerFormat().getLabel();
        if (!format.equals(dev.bryrich.credapp.application.AnswerFormat.AS_SAVED.getLabel())) {
            detail += ", " + format;
        }
        if (!field.defaultValue().isEmpty()) {
            detail += ", or \"" + field.defaultValue() + "\" when empty";
        }
        return detail;
    }

    private static Pick notABox() {
        return problem("That isn't a box CredCloud can fill. It types, picks options and ticks boxes; "
                + "click the box itself or its label.");
    }

    private static Pick problem(String message) {
        return new Pick(null, message);
    }
}
