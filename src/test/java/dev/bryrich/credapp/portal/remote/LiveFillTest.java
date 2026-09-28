package dev.bryrich.credapp.portal.remote;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Filling a provider's answers into a small local portal, in CredCloud's browser. */
class LiveFillTest {

    private static HttpServer portal;
    private static String base;
    private RemoteSession session;
    private final List<List<String>> ended = new ArrayList<>();

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
                <label>First name <input id="first"></label>
                <label for="specialty">Specialty</label> <select id="specialty"><option value="">Choose</option>
                  <option value="FM">Family medicine</option><option value="IM">Internal medicine</option></select>
                <fieldset><legend>US citizen</legend>
                  <label><input type="radio" name="citizen" id="yes" value="Y"> Yes</label>
                  <label><input type="radio" name="citizen" id="no" value="N"> No</label></fieldset>
                <label><input type="checkbox" id="spanish"> Spanish</label>
                <label>Submit application <input type="submit" id="send" value="Send"></label>
                <iframe src="/frame" width="600" height="200"></iframe>""");
        page("/frame", "<label>NPI <input id=\"npi\"></label>");
        page("/other", "<label>First name <input id=\"first\"></label>");
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
    void fillsEveryKindOfBoxIncludingInsideAFrame_andNeverPressesButtons() throws Exception {
        LiveFill fill = fill(List.of(
                field("First name", "label", "First name", "text", ""),
                field("Specialty", "label", "Specialty", "select", ""),
                field("Citizen yes", "css", "#yes", "radio", ""),
                field("Citizen no", "css", "#no", "radio", ""),
                field("Spanish", "label", "Spanish", "checkbox", ""),
                field("NPI", "label", "NPI", "text", ""),
                field("Send", "css", "#send", "text", "")),
                Map.of(0, "Daniel", 1, "Family medicine", 2, "Yes", 3, "Yes", 4, "English, Spanish", 5, "1234567893",
                        6, "anything"));
        open(base + "/enroll");

        LiveFill.Outcome outcome = session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(value("#first")).isEqualTo("Daniel");
        assertThat(value("#specialty")).isEqualTo("FM");
        assertThat(checked("#yes")).isTrue();
        assertThat(checked("#no")).as("a yes/no option follows the answer").isFalse();
        assertThat(checked("#spanish")).isTrue();
        assertThat(session.call(page -> page.frames().get(1).inputValue("#npi")).get(10, TimeUnit.SECONDS))
                .isEqualTo("1234567893");
        assertThat(fill.filledIndexes()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(outcome.problem()).isTrue();
        assertThat(outcome.message()).startsWith("Filled 6 boxes on this page. 1 still to fill.")
                .contains("Send: the page has a button or something else");

        fill.end();
        fill.end();
        assertThat(ended).containsExactly(
                List.of("First name", "Specialty", "Citizen yes", "Citizen no", "Spanish", "NPI"), List.of("Send"));
    }

    @Test
    void saysWhenEveryBoxWithAnAnswerIsFilled() throws Exception {
        LiveFill fill = fill(List.of(
                field("First name", "label", "First name", "text", ""),
                field("DEA", "label", "DEA", "text", "")),
                Map.of(0, "Daniel", 1, ""));
        open(base + "/enroll");
        LiveFill.Outcome first = session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(first.message()).isEqualTo("Filled 1 box on this page. Every box with an answer is filled. "
                + "No answer in CredCloud for: DEA. Check each page, then submit it in the portal yourself.");
        LiveFill.Outcome again = session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(again.message()).startsWith("Every box with an answer is filled.");
        assertThat(again.problem()).isFalse();
    }

    @Test
    void fillsOnlyTheBoxesTaughtOnThisPageFirst() throws Exception {
        LiveFill fill = fill(List.of(
                field("First name", "label", "First name", "text", "/enroll"),
                field("First name again", "label", "First name", "text", "/other")),
                Map.of(0, "Daniel", 1, "Danny"));
        open(base + "/other");
        session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(value("#first")).isEqualTo("Danny");
        assertThat(fill.filledIndexes()).containsExactly(1);
    }

    @Test
    void wontTypeIntoAnotherSite() throws Exception {
        LiveFill fill = new LiveFill("https://portal.example.com/enroll", "Daniel Okafor",
                List.of(field("First name", "label", "First name", "text", "")), Map.of(0, "Daniel"),
                (filled, missed) -> { });
        open(base + "/enroll");
        LiveFill.Outcome outcome = session.call(fill::fillOn).get(20, TimeUnit.SECONDS);
        assertThat(outcome.message()).contains("isn't on the portal");
        assertThat(value("#first")).isEmpty();
    }

    @Test
    void samePortalMeansTheSameSite() {
        assertThat(LiveFill.samePortal("https://login.payer.com/x", "https://portal.payer.com/enroll")).isTrue();
        assertThat(LiveFill.samePortal("https://payer.com.evil.net/", "https://portal.payer.com/")).isFalse();
        assertThat(LiveFill.samePortal("chrome-error://chromewebdata/", "https://portal.payer.com/")).isFalse();
        assertThat(LiveFill.samePortal("about:blank", "https://portal.payer.com/")).isFalse();
    }

    @Test
    void aClosedBrowserAnswersAtOnce() throws Exception {
        open(base + "/enroll");
        session.close();
        session.awaitEnded(Duration.ofSeconds(20));
        var call = session.call(page -> page.url());
        assertThat(call).failsWithin(Duration.ofSeconds(2));
    }

    // ============ helpers ============

    private LiveFill fill(List<PortalField> fields, Map<Integer, String> answers) {
        return new LiveFill(base + "/enroll", "Daniel Okafor", fields, answers, (filled, missed) -> {
            ended.add(filled);
            ended.add(missed);
        });
    }

    private void open(String url) throws Exception {
        session = new RemoteSession("fill", url, RemoteSessionTest.options(null));
        session.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var update = session.awaitUpdate(-1, -1, Duration.ofMillis(200));
            if (update.status().url().equals(url) && update.status().title().equals("Portal")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The portal never loaded: " + session.status());
    }

    private String value(String selector) throws Exception {
        return session.call(page -> page.inputValue(selector)).get(10, TimeUnit.SECONDS);
    }

    private boolean checked(String selector) throws Exception {
        return session.call(page -> page.isChecked(selector)).get(10, TimeUnit.SECONDS);
    }

    private static PortalField field(String label, String by, String locator, String kind, String page) {
        return new PortalField(label, by, locator, kind, "provider.x", "", "", page);
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
