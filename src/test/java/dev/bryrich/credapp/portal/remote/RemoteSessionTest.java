package dev.bryrich.credapp.portal.remote;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives a real Chromium against a small local portal. Needs Playwright's Chromium: CI installs
 * it; elsewhere the tests are skipped when it can't start, or CREDAPP_TEST_CHROMIUM can point
 * at a Chromium to use.
 */
class RemoteSessionTest {

    private static HttpServer portal;
    private static String base;
    private RemoteSession session;

    static RemoteSession.Options options(String proxy) {
        String executable = System.getenv("CREDAPP_TEST_CHROMIUM");
        return new RemoteSession.Options(1024, 700, "America/New_York",
                executable == null || executable.isBlank() ? null : Path.of(executable), proxy);
    }

    @BeforeAll
    static void portal() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions();
            if (options(null).executable() != null) {
                launch.setExecutablePath(options(null).executable());
            }
            playwright.chromium().launch(launch).close();
        } catch (RuntimeException e) {
            assumeTrue(System.getenv("CI") != null, "No Chromium for Playwright here: " + e.getMessage());
            throw e;
        }
        portal = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        page("/", """
                <h1>Payer portal sign-in</h1>
                <label>Username <input id="user"></label>
                <label>Password <input id="pass" type="password"></label>
                <p><a id="help" href="/help" target="_blank">Help</a></p>
                <button id="ask" onclick="document.title = confirm('Leave?') ? 'left' : 'stayed'">Leave</button>""");
        page("/help", "<h1>Help</h1>");
        portal.start();
        base = "http://127.0.0.1:" + portal.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        if (portal != null) {
            portal.stop(0);
        }
    }

    @AfterEach
    void close() throws InterruptedException {
        if (session != null) {
            session.close();
            assertThat(session.awaitEnded(Duration.ofSeconds(20))).isTrue();
        }
    }

    @Test
    void showsThePortalAsPictures() throws Exception {
        session = open(base + "/", null);
        RemoteSession.Update update = until(u -> u.frame() != null && u.status().title().equals("Portal"));
        byte[] jpeg = Base64.getDecoder().decode(update.frame());
        assertThat(jpeg[0] & 0xff).isEqualTo(0xff);
        assertThat(jpeg[1] & 0xff).isEqualTo(0xd8);
        assertThat(update.status().state()).isEqualTo("open");
        assertThat(update.status().url()).isEqualTo(base + "/");
    }

    @Test
    void typesWhereSheClicks() throws Exception {
        session = open(base + "/", null);
        until(u -> u.status().title().equals("Portal"));
        BoundingBox user = box("#user");
        BoundingBox pass = box("#pass");
        session.input(List.of(
                click("down", user), click("up", user),
                new RemoteInput("type", 0, 0, null, 0, 0, 0, null, null, "dr.okafor"),
                new RemoteInput("key", 0, 0, null, 0, 0, 0, "Backspace", null, null),
                new RemoteInput("type", 0, 0, null, 0, 0, 0, null, null, "é"),
                click("down", pass), click("up", pass),
                new RemoteInput("paste", 0, 0, null, 0, 0, 0, null, null, "s3cret!"),
                // Cmd+A on her Mac selects all in the remote (Linux) browser, then typing replaces it.
                new RemoteInput("key", 0, 0, null, 0, 0, 0, "a", List.of("Meta"), null),
                new RemoteInput("type", 0, 0, null, 0, 0, 0, null, null, "new")));
        assertThat(value("#user")).isEqualTo("dr.okafoé");
        assertThat(value("#pass")).isEqualTo("new");
    }

    @Test
    void followsANewTabAndComesBack() throws Exception {
        session = open(base + "/", null);
        until(u -> u.status().title().equals("Portal"));
        BoundingBox help = box("#help");
        session.input(List.of(click("down", help), click("up", help)));
        until(u -> u.status().url().equals(base + "/help") && u.status().tabs() == 2);
        session.call(page -> {
            page.close();
            return null;
        }).get(10, TimeUnit.SECONDS);
        until(u -> u.status().url().equals(base + "/") && u.status().tabs() == 1);
    }

    @Test
    void answersCancelToThePortalsQuestionsAndSaysSo() throws Exception {
        session = open(base + "/", null);
        until(u -> u.status().title().equals("Portal"));
        BoundingBox ask = box("#ask");
        session.input(List.of(click("down", ask), click("up", ask)));
        RemoteSession.Update update = until(u -> u.status().message().contains("Leave?"));
        assertThat(update.status().message()).contains("CredCloud answered Cancel");
        assertThat(session.call(page -> page.title()).get(10, TimeUnit.SECONDS)).isEqualTo("stayed");
    }

    @Test
    void goesThroughTheProxyAndCantReachTheServer() throws Exception {
        try (EgressProxy proxy = EgressProxy.start()) {
            // The portal is on this machine's loopback, which the proxy never lets through.
            session = open(base.replace("http:", "https:") + "/", proxy.address());
            RemoteSession.Update update = until(u -> u.status().message().contains("only opens public https sites"));
            assertThat(update.status().state()).isEqualTo("open");
            assertThat(proxy.refused()).isPositive();
        }
    }

    @Test
    void endsWhenClosed() throws Exception {
        session = open(base + "/", null);
        until(u -> u.status().title().equals("Portal"));
        session.close();
        assertThat(session.awaitEnded(Duration.ofSeconds(20))).isTrue();
        assertThat(session.status().state()).isEqualTo("ended");
    }

    // ============ helpers ============

    private RemoteSession open(String url, String proxy) {
        RemoteSession opened = new RemoteSession("test", url, options(proxy));
        opened.start();
        return opened;
    }

    private RemoteSession.Update until(Predicate<RemoteSession.Update> done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        long frame = -1;
        long status = -1;
        RemoteSession.Update update = null;
        while (System.nanoTime() < deadline) {
            update = session.awaitUpdate(frame, status, Duration.ofSeconds(1));
            if (done.test(update)) {
                return update;
            }
            frame = update.frameVersion();
            status = update.statusVersion();
        }
        throw new AssertionError("Never happened. Last status: " + (update == null ? null : update.status()));
    }

    private BoundingBox box(String selector) throws Exception {
        return session.call(page -> page.locator(selector).boundingBox()).get(10, TimeUnit.SECONDS);
    }

    private String value(String selector) throws Exception {
        return session.call(page -> page.inputValue(selector)).get(10, TimeUnit.SECONDS);
    }

    private static RemoteInput click(String type, BoundingBox box) {
        return new RemoteInput(type, box.x + box.width / 2, box.y + box.height / 2, "left", 1, 0, 0, null, null, null);
    }

    private static void page(String path, String body) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + (path.equals("/") ? "Portal" : "Help")
                + "</title></head><body>" + body + "</body></html>";
        portal.createContext(path, exchange -> {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
