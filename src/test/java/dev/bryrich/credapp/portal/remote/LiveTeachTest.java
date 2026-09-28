package dev.bryrich.credapp.portal.remote;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.sun.net.httpserver.HttpServer;
import dev.bryrich.credapp.portal.PortalField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Picking boxes in a small local portal, in CredCloud's browser, and filling what was taught. */
class LiveTeachTest {

    private static HttpServer portal;
    private static String base;
    private RemoteSession session;

    @BeforeAll
    static void portal() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions();
            if (RemoteSessionTest.options(null).executable() != null) {
                launch.setExecutablePath(RemoteSessionTest.options(null).executable());
            }
            playwright.chromium().launch(launch).close();
        } catch (RuntimeException e) {
            assumeTrue(System.getenv("CI") != null, "No Chromium for Playwright here: " + e.getMessage());
            throw e;
        }
        portal = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        page("/enroll", """
                <style>body{margin:0;font:16px sans-serif} label,div{display:block;margin:12px}</style>
                <label>First name * <input id="first" name="first_name"></label>
                <div><input id="a1" placeholder="Phone"> <input id="a2" placeholder="Fax"></div>
                <label id="lang-label"><input type="checkbox" name="lang" value="es"> Spanish</label>
                <button id="send">Send</button>
                <iframe src="/frame" style="border:0;width:500px;height:120px;display:block"></iframe>""");
        page("/frame", "<style>body{margin:0;font:16px sans-serif}</style><div style=\"margin:20px\"><label>NPI <input id=\"npi\"></label></div>");
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
            session.awaitEnded(Duration.ofSeconds(20));
        }
    }

    @Test
    void picksBoxesByLabelOrSelector_insideFramesToo_andNotButtons() throws Exception {
        LiveTeach teach = teach(List.of());
        open(base + "/enroll");

        LiveTeach.Pick first = pickAt(teach, "#first");
        assertThat(first.box()).isEqualTo(new LiveTeach.Picked("First name", "label", "First name *", "text", "/enroll"));

        LiveTeach.Pick phone = pickAt(teach, "#a1");
        assertThat(phone.box().by()).isEqualTo("css");
        assertThat(phone.box().locator()).isEqualTo("#a1");
        assertThat(phone.box().label()).as("the placeholder names it").isEqualTo("Phone");

        // Clicking the words of a label picks its box.
        BoundingBox words = box("#lang-label");
        LiveTeach.Pick spanish = session.call(page -> teach.pickAt(page, words.x + words.width - 20, words.y + words.height / 2))
                .get(10, TimeUnit.SECONDS);
        assertThat(spanish.box().kind()).isEqualTo("checkbox");
        assertThat(spanish.box().locator()).isEqualTo("Spanish");

        assertThat(pickAt(teach, "#send").problem()).contains("isn't a box");

        BoundingBox frame = box("iframe");
        LiveTeach.Pick npi = session.call(page -> teach.pickAt(page, frame.x + 60, frame.y + 30)).get(10, TimeUnit.SECONDS);
        assertThat(npi.problem()).isNull();
        assertThat(npi.box()).isEqualTo(new LiveTeach.Picked("NPI", "label", "NPI", "text", "/enroll"));
    }

    @Test
    void whatWasTaughtFills() throws Exception {
        LiveTeach teach = teach(List.of());
        open(base + "/enroll");
        for (String selector : List.of("#first", "#a1")) {
            LiveTeach.Picked picked = pickAt(teach, selector).box();
            teach.add(new PortalField(picked.label(), picked.by(), picked.locator(), picked.kind(),
                    selector.equals("#first") ? "provider.first_name" : "", "", selector.equals("#a1") ? "801-555-0142" : "",
                    picked.page()));
        }
        assertThat(teach.unsaved()).isTrue();
        assertThat(teach.rows()).containsExactly(new LiveTeach.Row("First name", "Provider / First name"),
                new LiveTeach.Row("Phone", "always \"801-555-0142\""));

        LiveFill fill = new LiveFill(base + "/enroll", "Daniel Okafor", teach.fields(),
                Map.of(0, "Daniel", 1, "801-555-0142"), (filled, missed) -> { });
        session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(session.call(page -> page.inputValue("#first")).get(10, TimeUnit.SECONDS)).isEqualTo("Daniel");
        assertThat(session.call(page -> page.inputValue("#a1")).get(10, TimeUnit.SECONDS)).isEqualTo("801-555-0142");
    }

    @Test
    void startsFromTheCurrentBoxesAndChecksWhatsAdded() {
        PortalField existing = new PortalField("NPI", "label", "NPI", "text", "provider.npi", "DIGITS", "", "/enroll");
        LiveTeach teach = teach(List.of(existing));
        assertThat(teach.rows()).containsExactly(new LiveTeach.Row("NPI", "Provider / NPI, Digits only"));
        assertThat(teach.unsaved()).isFalse();

        assertThatThrownBy(() -> teach.add(new PortalField("Password", "label", "Password", "text",
                "provider.password", "", "", ""))).hasMessageContaining("Choose listed data");
        assertThatThrownBy(() -> teach.add(new PortalField("Blank", "label", "Blank", "text", "", "", "", "")))
                .hasMessageContaining("Choose data or a fixed answer");

        teach.remove(0);
        assertThat(teach.count()).isZero();
        assertThat(teach.unsaved()).isTrue();
        teach.saved();
        assertThat(teach.unsaved()).isFalse();
    }

    @Test
    void wontPickOnAnotherSite() throws Exception {
        LiveTeach teach = new LiveTeach(1, "https://portal.example.com/enroll", List.of(), Map.of());
        open(base + "/enroll");
        assertThat(pickAt(teach, "#first").problem()).contains("isn't on the portal you're teaching");
    }

    // ============ helpers ============

    private LiveTeach teach(List<PortalField> current) {
        return new LiveTeach(1, base + "/enroll", current,
                Map.of("provider.first_name", "Provider / First name", "provider.npi", "Provider / NPI"));
    }

    private LiveTeach.Pick pickAt(LiveTeach teach, String selector) throws Exception {
        BoundingBox box = box(selector);
        return session.call(page -> teach.pickAt(page, box.x + box.width / 2, box.y + box.height / 2))
                .get(10, TimeUnit.SECONDS);
    }

    private BoundingBox box(String selector) throws Exception {
        return session.call(page -> page.locator(selector).boundingBox()).get(10, TimeUnit.SECONDS);
    }

    private void open(String url) throws Exception {
        session = new RemoteSession("teach", url, RemoteSessionTest.options(null));
        session.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var update = session.awaitUpdate(-1, -1, Duration.ofMillis(200));
            if (update.status().url().equals(url) && update.status().title().equals("Portal")) {
                // The frame loads after the page; wait for it too.
                session.call(page -> {
                    page.frames().getLast().waitForLoadState();
                    return null;
                }).get(10, TimeUnit.SECONDS);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The portal never loaded: " + session.status());
    }

    private static void page(String path, String body) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Portal</title></head><body>"
                + body + "</body></html>";
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
