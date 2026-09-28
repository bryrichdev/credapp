package dev.bryrich.credapp.portal.remote;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import dev.bryrich.credapp.portal.PortalField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One provider's answers for one portal template, being filled in CredCloud's browser. The
 * answers live only here, in memory, for as long as the browser is open; the database keeps
 * just which boxes were filled.
 * <p>
 * {@link #fillOn} runs on the session's thread: it types into every frame of the page that
 * belongs to the portal, which reaches forms inside iframes too.
 */
public final class LiveFill {

    /** A box of the template with its answer, for the list on the page. */
    public record Box(int index, String label, String value, boolean filled) {
    }

    /** What a press of Fill this page did. */
    public record Outcome(String message, boolean problem) {
    }

    /** Told once, when the browser closes, which boxes were filled and which weren't. */
    public interface Ending {
        void ended(List<String> filled, List<String> missed);
    }

    record Item(int index, PortalField field, String value) {
    }

    private final String startUrl;
    private final String providerName;
    private final List<PortalField> fields;
    private final Map<Integer, String> answers;
    private final Ending ending;
    private final Set<Integer> filled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ended = new AtomicBoolean();

    /**
     * @param answers each answer by its field's index in {@code fields}
     */
    public LiveFill(String startUrl, String providerName, List<PortalField> fields, Map<Integer, String> answers,
                    Ending ending) {
        this.startUrl = startUrl;
        this.providerName = providerName;
        this.fields = List.copyOf(fields);
        this.answers = Map.copyOf(answers);
        this.ending = ending;
    }

    public String providerName() {
        return providerName;
    }

    public int total() {
        return fields.size();
    }

    public int filledCount() {
        return filled.size();
    }

    public List<Integer> filledIndexes() {
        return filled.stream().sorted().toList();
    }

    public List<Box> boxes() {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            boxes.add(new Box(i, fields.get(i).label(), answers.getOrDefault(i, ""), filled.contains(i)));
        }
        return boxes;
    }

    /** Fills what it can of the page she's on. Runs on the session's thread. */
    public Outcome fillOn(Page page) {
        if (!PortalScript.samePortal(page.url(), startUrl)) {
            return new Outcome("This page isn't on the portal the fill is for, so CredCloud won't type into it.", true);
        }
        List<Item> open = openFor(page.url());
        List<Integer> done = new ArrayList<>();
        Set<String> problems = new LinkedHashSet<>();
        List<Item> left = new ArrayList<>(open);
        for (Frame frame : page.frames()) {
            if (left.isEmpty()) {
                break;
            }
            if (frame.isDetached() || !PortalScript.samePortal(frame.url(), startUrl)) {
                continue;
            }
            try {
                JsonObject report = PortalScript.run(frame, Map.of("action", "fill", "items", left));
                report.getAsJsonArray("filled").forEach(index -> done.add(index.getAsInt()));
                report.getAsJsonArray("problems").forEach(problem -> problems.add(problem.getAsString()));
                left.removeIf(item -> done.contains(item.index()));
            } catch (PlaywrightException e) {
                // The frame went away or navigated mid-fill; the next press picks it up.
            }
        }
        markFilled(done);
        List<String> noAnswer = new ArrayList<>();
        int stillToFill = 0;
        for (int i = 0; i < fields.size(); i++) {
            if (answers.getOrDefault(i, "").isEmpty()) {
                noAnswer.add(fields.get(i).label());
            } else if (!filled.contains(i)) {
                stillToFill++;
            }
        }
        if (open.isEmpty() || (stillToFill == 0 && problems.isEmpty())) {
            String message = done.isEmpty() ? "" : "Filled " + boxes(done.size()) + " on this page. ";
            message += "Every box with an answer is filled.";
            if (!noAnswer.isEmpty()) {
                message += " No answer in CredCloud for: " + String.join(", ", noAnswer) + ".";
            }
            return new Outcome(message + " Check each page, then submit it in the portal yourself.", false);
        }
        if (done.isEmpty() && problems.isEmpty()) {
            return new Outcome("CredCloud didn't find any of this template's boxes on this page. Go to the form, "
                    + "then press Fill this page again.", false);
        }
        String message = "Filled " + boxes(done.size()) + " on this page. " + stillToFill + " still to fill.";
        if (!problems.isEmpty()) {
            message += " Left alone: " + String.join("; ", problems) + ".";
        }
        return new Outcome(message, !problems.isEmpty());
    }

    private static String boxes(int count) {
        return count + (count == 1 ? " box" : " boxes");
    }

    /**
     * The boxes still to fill here: the ones taught on this page and any taught without a page,
     * or every open box when none were taught on this page (a portal that moved things around).
     */
    List<Item> openFor(String pageUrl) {
        List<Item> open = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String value = answers.getOrDefault(i, "");
            if (!filled.contains(i) && !value.isEmpty()) {
                open.add(new Item(i, fields.get(i), value));
            }
        }
        String here = PortalScript.path(pageUrl);
        boolean taughtHere = open.stream().anyMatch(item -> item.field().page().equals(here));
        return taughtHere
                ? open.stream().filter(item -> item.field().page().equals(here) || item.field().page().isEmpty()).toList()
                : open;
    }

    void markFilled(Collection<Integer> indexes) {
        filled.addAll(indexes);
    }

    /** Records the fill once, however the browser closed. */
    void end() {
        if (ended.compareAndSet(false, true)) {
            List<String> done = new ArrayList<>();
            List<String> missed = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                (filled.contains(i) ? done : missed).add(fields.get(i).label());
            }
            ending.ended(done, missed);
        }
    }

    /** Same test as the fill uses, kept here for the tests that call it. */
    static boolean samePortal(String url, String startUrl) {
        return PortalScript.samePortal(url, startUrl);
    }
}
