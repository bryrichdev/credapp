package dev.bryrich.credcloud.runner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One job in one portal tab. The tab carries the panel on every page. Presses in the panel
 * arrive through a binding and are queued, then handled on the runner's thread by
 * {@link #process()}, since Playwright must only be called from one thread.
 *
 * <p>A fill job types answers when she presses Fill this page, reports which boxes it filled
 * when she presses Done, and leaves the tab open for her to check and submit. A learn job
 * records the boxes she picks and saves them as the template's next version.
 */
final class PortalSession {

    /** How the session talks to the CredCloud server. */
    interface Server {
        void filled(long jobId, List<String> filled, List<String> missed);

        int learned(long jobId, List<Model.Field> fields);

        void cancel(long jobId);
    }

    static final String PANEL = loadPanel();
    private static final Gson GSON = new Gson();

    private final Model.Job job;
    private final Server server;
    private final Page page;
    private final Queue<String> actions = new ConcurrentLinkedQueue<>();
    private final List<Model.Field> fields;
    private final List<String> answers = new ArrayList<>();
    private final Set<Integer> filled = new LinkedHashSet<>();
    private JsonObject pending;
    private boolean picking;
    private boolean finished;
    private String message = "";
    private String tone = "";

    PortalSession(BrowserContext context, Model.Job job, Server server) {
        this.job = job;
        this.server = server;
        this.fields = new ArrayList<>(job.fields() == null ? List.of() : job.fields());
        if (job.fill()) {
            for (int i = 0; i < fields.size(); i++) {
                answers.add("");
            }
            for (Model.Answer answer : job.answers()) {
                if (answer.field() >= 0 && answer.field() < answers.size()) {
                    answers.set(answer.field(), answer.value());
                }
            }
            message = "Sign in, open the form, then press Fill this page.";
        } else {
            message = fields.isEmpty() ? "Sign in and open the form. Then press Pick a box on the page."
                    : "Starting from version " + job.revision() + ". Pick more boxes or remove any that changed.";
        }
        this.page = context.newPage();
        page.exposeBinding("credcloudRunner", (source, args) -> {
            if (args.length == 1 && args[0] instanceof String json) {
                actions.add(json);
            }
            return null;
        });
        page.addInitScript(PANEL);
        page.onDOMContentLoaded(p -> actions.add("{\"action\":\"render\"}"));
        page.onClose(p -> actions.add("{\"action\":\"closed\"}"));
        page.navigate(job.startUrl());
    }

    Page page() {
        return page;
    }

    boolean finished() {
        return finished;
    }

    /** Handles whatever she pressed since the last call. */
    void process() {
        String json;
        while ((json = actions.poll()) != null) {
            JsonObject action = GSON.fromJson(json, JsonObject.class);
            try {
                handle(action.get("action").getAsString(), action);
            } catch (RuntimeException e) {
                say(e instanceof Refused ? e.getMessage() : "Something went wrong: " + e.getMessage(), "error");
            }
            if (!page.isClosed()) {
                render();
            }
        }
    }

    private void handle(String action, JsonObject data) {
        if (finished && !action.equals("render")) {
            return;
        }
        switch (action) {
            case "render" -> {
            }
            case "closed" -> {
                if (!finished) {
                    server.cancel(job.id());
                    finished = true;
                }
            }
            case "cancel" -> {
                server.cancel(job.id());
                finished = true;
                say("Cancelled. Nothing was saved.", "done");
            }
            case "fill" -> fill();
            case "done" -> done();
            case "startPicking" -> {
                picking = true;
                say("Click a box on the page. Clicks go to the runner, not the portal, until you stop.", "");
            }
            case "stopPicking" -> {
                picking = false;
                say("", "");
            }
            case "notABox" -> say("That isn't a box the runner can fill. It only types, picks options and ticks boxes.", "error");
            case "picked" -> picked(data);
            case "add" -> add(data);
            case "discard" -> {
                pending = null;
                say("", "");
            }
            case "remove" -> {
                int index = data.get("index").getAsInt();
                if (index >= 0 && index < fields.size()) {
                    fields.remove(index);
                }
            }
            case "save" -> save();
            default -> {
            }
        }
    }

    private void fill() {
        if (!job.fill()) {
            return;
        }
        if (!Filler.samePortal(page.url(), job.startUrl())) {
            throw new Refused("This page isn't on the portal the fill was sent for, so the runner won't type into it.");
        }
        Filler.Report report = Filler.fill(page, fields, answers, filled);
        filled.addAll(report.filled());
        int left = (int) java.util.stream.IntStream.range(0, fields.size()).filter(i -> !filled.contains(i)).count();
        String text = "Filled " + report.filled().size() + " boxes on this page. " + left + " not filled yet."
                + (report.problems().isEmpty() ? "" : " Left alone: " + String.join("; ", report.problems()) + ".");
        say(text, report.problems().isEmpty() ? "" : "error");
    }

    private void done() {
        if (!job.fill()) {
            return;
        }
        List<String> done = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            (filled.contains(i) ? done : missed).add(fields.get(i).label());
        }
        server.filled(job.id(), done, missed);
        finished = true;
        say("Filled " + done.size() + " of " + fields.size() + " boxes." + (missed.isEmpty() ? ""
                : " Not filled: " + String.join(", ", missed) + ".")
                + " Check every page, then submit it yourself.", "done");
    }

    private void picked(JsonObject data) {
        if (job.fill() || !picking) {
            return;
        }
        String label = text(data, "label");
        String labelText = text(data, "labelText");
        String css = text(data, "css");
        String by;
        String locator;
        // By label when the label names exactly one box: it survives most redesigns.
        if (!labelText.isEmpty() && count(Filler.locate(page, field("label", labelText))) == 1) {
            by = "label";
            locator = labelText;
        } else if (!css.isEmpty() && count(page.locator(css)) == 1) {
            by = "css";
            locator = css;
        } else {
            throw new Refused("The runner couldn't pin that box down. Try clicking its label instead.");
        }
        pending = new JsonObject();
        pending.addProperty("label", label.isEmpty() ? "Box " + (fields.size() + 1) : label);
        pending.addProperty("by", by);
        pending.addProperty("locator", locator);
        pending.addProperty("kind", text(data, "kind"));
        pending.addProperty("page", text(data, "page"));
        picking = false;
        say("", "");
    }

    private void add(JsonObject data) {
        if (pending == null) {
            return;
        }
        String source = text(data, "source");
        String fixed = text(data, "defaultValue");
        if (source.isEmpty() && fixed.isEmpty()) {
            throw new Refused("Choose data, or type a fixed answer.");
        }
        String label = text(data, "label");
        fields.add(new Model.Field(label.isEmpty() ? text(pending, "label") : label, text(pending, "by"),
                text(pending, "locator"), text(pending, "kind"), source, text(data, "format"), fixed,
                text(pending, "page")));
        pending = null;
        picking = true;
        say("Added. Click the next box, or stop picking.", "");
    }

    private void save() {
        if (job.fill() || fields.isEmpty()) {
            throw new Refused("Pick at least one box first.");
        }
        int revision = server.learned(job.id(), List.copyOf(fields));
        finished = true;
        picking = false;
        say("Saved as version " + revision + ". It's ready to fill in CredCloud.", "done");
    }

    private void render() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", job.fill() ? "fill" : "learn");
        state.put("title", job.payerName() + " / " + job.templateName());
        state.put("provider", job.providerName());
        state.put("message", message);
        state.put("tone", tone);
        state.put("finished", finished);
        state.put("picking", picking);
        if (!job.fill()) {
            List<Map<String, String>> list = new ArrayList<>();
            Map<String, String> labels = new LinkedHashMap<>();
            job.sources().forEach(s -> labels.put(s.key(), s.label()));
            for (Model.Field f : fields) {
                list.add(Map.of("label", f.label(), "detail",
                        f.source().isEmpty() ? "always \"" + f.defaultValue() + "\"" : labels.getOrDefault(f.source(), f.source())));
            }
            state.put("fields", list);
            state.put("pending", pending == null ? null : GSON.fromJson(pending, Map.class));
            state.put("sources", job.sources());
            state.put("formats", job.formats());
        }
        try {
            // Through JSON, so records arrive as plain objects.
            page.evaluate("s => window.__credcloud && window.__credcloud.render(s)",
                    GSON.fromJson(GSON.toJson(state), Map.class));
        } catch (PlaywrightException e) {
            // The page navigated mid-render; the next page's load renders again.
        }
    }

    private void say(String text, String kind) {
        message = text;
        tone = kind;
    }

    private static int count(com.microsoft.playwright.Locator locator) {
        try {
            return locator.count();
        } catch (PlaywrightException e) {
            return 0;
        }
    }

    private static Model.Field field(String by, String locator) {
        return new Model.Field("", by, locator, "", "", "", "", "");
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
    }

    private static String loadPanel() {
        try (InputStream in = PortalSession.class.getResourceAsStream("/panel.js")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("panel.js is missing from the runner", e);
        }
    }

    /** A message for her, not a bug. */
    static final class Refused extends RuntimeException {
        Refused(String message) {
            super(message);
        }
    }
}
