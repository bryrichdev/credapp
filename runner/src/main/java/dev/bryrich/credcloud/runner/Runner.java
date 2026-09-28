package dev.bryrich.credcloud.runner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.PlaywrightException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The runner's loop: keep the CredCloud tab open, pair if needed, ask for a job every few
 * seconds, and hand each job to a {@link PortalSession}. One job at a time; the tab of a
 * finished fill stays open for her to check and submit.
 */
final class Runner implements PortalSession.Server {

    private static final Gson GSON = new Gson();

    private final BrowserContext context;
    private final Config config;
    private final Bridge bridge;
    private final Duration pollEvery;
    private PortalSession current;
    private long nextPoll;

    Runner(BrowserContext context, Config config, Duration pollEvery) {
        this.context = context;
        this.config = config;
        this.bridge = new Bridge(context, config.server());
        this.pollEvery = pollEvery;
    }

    /** Runs until Chrome is closed. */
    void run() {
        while (true) {
            try {
                step();
                bridge.page().waitForTimeout(250);
            } catch (PlaywrightException e) {
                if (context.pages().isEmpty() || isClosed(e)) {
                    return;
                }
                bridge.show("offline", "Can't reach CredCloud right now. Trying again…");
                sleep();
            }
        }
    }

    /** One pass of the loop. Tests call this directly. */
    void step() {
        bridge.page();
        String event;
        while ((event = bridge.nextEvent()) != null) {
            JsonObject data = GSON.fromJson(event, JsonObject.class);
            if ("paired".equals(data.get("action").getAsString())) {
                config.token(data.get("token").getAsString());
                nextPoll = 0;
            }
        }
        if (current != null) {
            current.process();
            if (current.finished()) {
                current = null;
            } else {
                bridge.show("working", "Working on a job in another tab.");
                return;
            }
        }
        if (!bridge.ready()) {
            return; // She's getting through Cloudflare or signing in to Access.
        }
        if (!config.paired()) {
            bridge.show("pairing", "Enter the code from CredCloud > My account > Runners.");
            return;
        }
        if (System.nanoTime() < nextPoll) {
            return;
        }
        nextPoll = System.nanoTime() + pollEvery.toNanos();
        Bridge.Response response = bridge.call("GET", "/runner/api/jobs/next", config.token(), null);
        switch (response.status()) {
            case 200 -> {
                Model.Job job = GSON.fromJson(response.body(), Model.Job.class);
                bridge.show("working", (job.fill() ? "Filling " + job.providerName() + " into " : "Learning ")
                        + job.payerName() + " / " + job.templateName() + " in a new tab.");
                try {
                    current = new PortalSession(context, job, this);
                } catch (PlaywrightException e) {
                    // The portal wouldn't open (a wrong address, or it's down). Don't take the job again.
                    cancel(job.id());
                    bridge.show("waiting", "Couldn't open " + job.startUrl() + ", so that job was cancelled. "
                            + "Check the portal's address in CredCloud.");
                }
            }
            case 204 -> bridge.show("waiting", "Connected. Waiting for a job from CredCloud.");
            case 401 -> {
                config.token(null);
                bridge.show("pairing", "This runner was revoked or its code expired. Enter a new code from CredCloud.");
            }
            default -> bridge.show("offline", "CredCloud answered " + response.status() + ". Trying again…");
        }
    }

    PortalSession current() {
        return current;
    }

    @Override
    public void filled(long jobId, List<String> filled, List<String> missed) {
        post("/runner/api/jobs/" + jobId + "/filled", Map.of("filled", filled, "missed", missed));
    }

    @Override
    public int learned(long jobId, List<Model.Field> fields) {
        Bridge.Response response = post("/runner/api/jobs/" + jobId + "/learned", Map.of("fields", fields));
        return GSON.fromJson(response.body(), JsonObject.class).get("revision").getAsInt();
    }

    @Override
    public void cancel(long jobId) {
        post("/runner/api/jobs/" + jobId + "/cancel", Map.of());
    }

    private Bridge.Response post(String path, Object body) {
        Bridge.Response response = bridge.call("POST", path, config.token(), GSON.toJson(body));
        if (response.status() >= 400) {
            String message = "CredCloud didn't accept that (" + response.status() + ").";
            try {
                JsonObject error = GSON.fromJson(response.body(), JsonObject.class);
                if (error != null && error.has("error")) {
                    message = error.get("error").getAsString();
                }
            } catch (RuntimeException notJson) {
                // Keep the generic message.
            }
            throw new PortalSession.Refused(message);
        }
        return response;
    }

    private static boolean isClosed(PlaywrightException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("closed") || message.contains("Target page, context or browser has been closed");
    }

    private static void sleep() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
