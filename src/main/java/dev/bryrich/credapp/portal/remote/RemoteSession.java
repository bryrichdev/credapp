package dev.bryrich.credapp.portal.remote;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.ServiceWorkerPolicy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * One Chromium that CredCloud runs for one coordinator, shown to her as a live picture she
 * clicks and types into. She signs in to the portal herself, so CredCloud never keeps her
 * portal password.
 * <p>
 * Playwright objects must only be used from one thread, and their events (the frames of the
 * picture) only arrive while that thread is inside a Playwright call. So each session has its
 * own thread: it runs queued work, and in between waits on the page in short steps, which lets
 * the frames through.
 */
public final class RemoteSession {

    /**
     * How sessions start.
     *
     * @param executable a Chromium to use instead of Playwright's own, or null
     * @param proxy      the {@link EgressProxy} address, or null to connect directly (tests only)
     */
    public record Options(int width, int height, String timezone, Path executable, String proxy) {
        public static Options defaults(String proxy) {
            return new Options(1280, 800, "America/New_York", null, proxy);
        }
    }

    /**
     * What the page tells her about the browser.
     *
     * @param state   starting, open, ended or failed
     * @param message something that happened she should know about, or empty
     */
    public record Status(String state, String url, String title, String message, int tabs) {
        public boolean over() {
            return state.equals("ended") || state.equals("failed");
        }
    }

    /** The newest picture (a base64 JPEG) and status, each with a counter so a viewer sees what changed. */
    public record Update(long frameVersion, String frame, long statusVersion, Status status) {
    }

    private static final Duration STEP = Duration.ofMillis(30);

    private final String id;
    private final String startUrl;
    private final Options options;
    private final BlockingQueue<Runnable> work = new LinkedBlockingQueue<>();
    private final Object lock = new Object();
    private final Thread thread;
    private volatile boolean closing;
    private volatile Instant lastUsed = Instant.now();

    // Guarded by lock.
    private String frame;
    private long frameVersion;
    private Status status;
    private long statusVersion;

    // Only touched on the session thread.
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private CDPSession screencast;
    private final List<Page> pages = new ArrayList<>();
    private String message = "";

    public RemoteSession(String id, String startUrl, Options options) {
        this.id = id;
        this.startUrl = startUrl;
        this.options = options;
        this.status = new Status("starting", startUrl, "", "", 0);
        this.thread = Thread.ofPlatform().name("remote-browser-" + id).daemon().unstarted(this::run);
    }

    public String id() {
        return id;
    }

    public void start() {
        thread.start();
    }

    /** When she last clicked, typed or watched. */
    public Instant lastUsed() {
        return lastUsed;
    }

    public void touch() {
        lastUsed = Instant.now();
    }

    public Status status() {
        synchronized (lock) {
            return status;
        }
    }

    /** Waits until the picture or status is newer than what the viewer has, or the time is up. */
    public Update awaitUpdate(long seenFrame, long seenStatus, Duration wait) throws InterruptedException {
        long deadline = System.nanoTime() + wait.toNanos();
        synchronized (lock) {
            while (frameVersion == seenFrame && statusVersion == seenStatus && !status.over()) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    break;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, left);
            }
            return new Update(frameVersion, frame, statusVersion, status);
        }
    }

    /** Passes on what she did, in order. */
    public void input(List<RemoteInput> events) {
        touch();
        List<RemoteInput> copy = List.copyOf(events);
        work.add(() -> copy.forEach(this::apply));
    }

    public void back() {
        touch();
        work.add(() -> navigation(() -> page.goBack()));
    }

    public void reload() {
        touch();
        work.add(() -> navigation(() -> page.reload()));
    }

    /**
     * Runs something against the page she's looking at, on the session's thread. Filling uses
     * this; so do tests.
     */
    public <T> CompletableFuture<T> call(Function<Page, T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        work.add(() -> {
            try {
                result.complete(action.apply(page));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** Ends the session. Chromium and everything it held, cookies included, go with it. */
    public void close() {
        closing = true;
        work.add(() -> {
        });
    }

    public boolean awaitEnded(Duration wait) throws InterruptedException {
        return thread.join(wait);
    }

    // ============ on the session thread ============

    private void run() {
        try {
            open();
            while (!closing) {
                Runnable next = work.poll();
                if (next != null) {
                    safely(next);
                } else if (page != null) {
                    try {
                        page.waitForTimeout(STEP.toMillis());
                    } catch (PlaywrightException e) {
                        // The page closed under us; its close handler has moved us on.
                        Thread.sleep(STEP.toMillis());
                    }
                } else {
                    Thread.sleep(STEP.toMillis());
                }
            }
            setStatus("ended", "");
        } catch (Throwable e) {
            setStatus("failed", "CredCloud's browser stopped: " + reason(String.valueOf(e.getMessage())));
        } finally {
            shutdown();
            synchronized (lock) {
                if (!status.over()) {
                    status = new Status("ended", status.url(), status.title(), status.message(), 0);
                    statusVersion++;
                }
                lock.notifyAll();
            }
        }
    }

    private void open() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--disable-dev-shm-usage",
                        "--force-webrtc-ip-handling-policy=disable_non_proxied_udp"));
        if (options.executable() != null) {
            launch.setExecutablePath(options.executable());
        }
        if (options.proxy() != null) {
            launch.setProxy(new Proxy(options.proxy()));
        }
        browser = playwright.chromium().launch(launch);
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(options.width(), options.height())
                .setUserAgent(userAgent())
                .setLocale("en-US")
                .setTimezoneId(options.timezone())
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                .setAcceptDownloads(false));
        context.setDefaultNavigationTimeout(30_000);
        context.setDefaultTimeout(10_000);
        context.onPage(this::opened);
        Page first = context.newPage();
        if (page != first) {
            opened(first);
        }
        setStatus("open", "");
        navigation(() -> page.navigate(startUrl));
    }

    /** Chromium's own agent string, minus the "Headless" that some portals turn away. */
    private String userAgent() {
        try (BrowserContext probe = browser.newContext()) {
            String agent = (String) probe.newPage().evaluate("navigator.userAgent");
            return agent.replace("HeadlessChrome", "Chrome");
        }
    }

    private void opened(Page opened) {
        if (pages.contains(opened)) {
            return;
        }
        pages.add(opened);
        opened.onClose(this::closed);
        opened.onDialog(this::dialog);
        opened.onFrameNavigated(navigated -> {
            if (navigated == opened.mainFrame()) {
                // Chromium's own error page keeps the message that says why it's showing.
                if (!navigated.url().startsWith("chrome-error:")) {
                    message = "";
                }
                work.add(this::refresh);
            }
        });
        opened.onLoad(loaded -> work.add(this::refresh));
        show(opened);
    }

    /** A portal's new tab or window takes over the picture; when it closes, the one before comes back. */
    private void closed(Page gone) {
        pages.remove(gone);
        if (gone != page) {
            refresh();
        } else if (pages.isEmpty()) {
            message = "The portal closed its window.";
            closing = true;
        } else {
            show(pages.getLast());
        }
    }

    private void dialog(Dialog dialog) {
        message = dialog.type().equals("alert")
                ? "The portal said: " + dialog.message()
                : "The portal asked: \"" + dialog.message() + "\" CredCloud answered Cancel.";
        dialog.dismiss();
        refresh();
    }

    private void show(Page shown) {
        if (screencast != null) {
            try {
                screencast.detach();
            } catch (PlaywrightException ignored) {
                // its page already closed
            }
        }
        page = shown;
        CDPSession cdp = context.newCDPSession(shown);
        screencast = cdp;
        cdp.on("Page.screencastFrame", event -> {
            synchronized (lock) {
                frame = event.get("data").getAsString();
                frameVersion++;
                lock.notifyAll();
            }
            JsonObject ack = new JsonObject();
            ack.addProperty("sessionId", event.get("sessionId").getAsInt());
            try {
                cdp.send("Page.screencastFrameAck", ack);
            } catch (PlaywrightException ignored) {
                // switched away mid-frame
            }
        });
        JsonObject start = new JsonObject();
        start.addProperty("format", "jpeg");
        start.addProperty("quality", 70);
        start.addProperty("maxWidth", options.width());
        start.addProperty("maxHeight", options.height());
        cdp.send("Page.startScreencast", start);
        shown.bringToFront();
        refresh();
    }

    private void refresh() {
        if (page == null || page.isClosed()) {
            return;
        }
        String title;
        try {
            title = page.title();
        } catch (PlaywrightException e) {
            title = "";
        }
        synchronized (lock) {
            status = new Status(status.over() ? status.state() : "open", page.url(), title, message, pages.size());
            statusVersion++;
            lock.notifyAll();
        }
    }

    private void apply(RemoteInput event) {
        if (page == null || event == null || event.type() == null) {
            return;
        }
        double x = Math.clamp(event.x(), 0, options.width() - 1);
        double y = Math.clamp(event.y(), 0, options.height() - 1);
        var mouse = page.mouse();
        switch (event.type()) {
            case "move" -> mouse.move(x, y);
            case "down" -> {
                mouse.move(x, y);
                mouse.down(new com.microsoft.playwright.Mouse.DownOptions()
                        .setButton(button(event)).setClickCount(event.clickCount()));
            }
            case "up" -> {
                mouse.move(x, y);
                mouse.up(new com.microsoft.playwright.Mouse.UpOptions()
                        .setButton(button(event)).setClickCount(event.clickCount()));
            }
            case "wheel" -> {
                mouse.move(x, y);
                mouse.wheel(Math.clamp(event.dx(), -2000, 2000), Math.clamp(event.dy(), -2000, 2000));
            }
            case "key" -> {
                String chord = event.chord();
                if (chord != null) {
                    page.keyboard().press(chord);
                }
            }
            case "type" -> {
                if (event.text() != null && event.text().length() <= RemoteInput.MAX_TEXT) {
                    page.keyboard().type(event.text());
                }
            }
            case "paste" -> {
                if (event.text() != null && event.text().length() <= RemoteInput.MAX_TEXT) {
                    page.keyboard().insertText(event.text());
                }
            }
            default -> {
                // not something the page sends
            }
        }
    }

    private static MouseButton button(RemoteInput event) {
        return switch (event.mouseButton()) {
            case "right" -> MouseButton.RIGHT;
            case "middle" -> MouseButton.MIDDLE;
            default -> MouseButton.LEFT;
        };
    }

    /** Goes somewhere, and says so on the page if it didn't work rather than ending the session. */
    private void navigation(Runnable go) {
        try {
            go.run();
        } catch (PlaywrightException e) {
            String error = e.getMessage() == null ? "" : e.getMessage();
            message = error.contains("ERR_TUNNEL_CONNECTION_FAILED") || error.contains("ERR_PROXY")
                    ? "CredCloud's browser only opens public https sites, and this one isn't reachable that way."
                    : error.contains("Timeout") ? "The portal is taking a long time to load. It may still finish."
                    : "The portal didn't load: " + reason(error);
        }
        refresh();
    }

    private void safely(Runnable next) {
        try {
            next.run();
        } catch (PlaywrightException e) {
            message = "That didn't go through: " + reason(String.valueOf(e.getMessage()));
            refresh();
        }
    }

    private void setStatus(String state, String note) {
        synchronized (lock) {
            status = new Status(state, status.url(), status.title(), note.isEmpty() ? message : note, pages.size());
            statusVersion++;
            lock.notifyAll();
        }
    }

    private void shutdown() {
        pages.clear();
        page = null;
        for (AutoCloseable closeable : new AutoCloseable[]{context, browser, playwright}) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // going away regardless
                }
            }
        }
    }

    /** Playwright's "net::ERR_NAME_NOT_RESOLVED at https://..." without the call log around it. */
    private static String reason(String error) {
        var net = java.util.regex.Pattern.compile("net::ERR_[A-Z_]+").matcher(error);
        return net.find() ? net.group() : firstLine(error.replaceFirst("^Error \\{\\s*", ""));
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) : line;
    }
}
