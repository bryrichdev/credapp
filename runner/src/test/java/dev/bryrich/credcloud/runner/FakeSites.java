package dev.bryrich.credcloud.runner;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A fake payer portal and a fake CredCloud, served from inside the browser with routes, so the
 * tests need no network. The portal sends a strict Content-Security-Policy, as real ones do.
 */
final class FakeSites {

    static final String PORTAL = "https://portal.test";
    static final String SERVER = "https://credcloud.test";
    static final String STRICT_CSP = "default-src 'self'; script-src 'self'; style-src 'self'";

    private FakeSites() {
    }

    /** Headless Chromium; CHROMIUM_PATH picks a browser already on the machine. */
    static Browser launch(Playwright playwright) {
        var options = new BrowserType.LaunchOptions().setHeadless(true);
        String path = System.getenv("CHROMIUM_PATH");
        if (path != null && !path.isBlank()) {
            options.setExecutablePath(Path.of(path));
        }
        return playwright.chromium().launch(options);
    }

    static void servePortal(BrowserContext context) {
        context.route(PORTAL + "/**", route -> {
            String path = URI.create(route.request().url()).getPath();
            switch (path) {
                case "/enroll" -> html(route, resource("/enroll.html"));
                case "/count-submits.js" -> fulfill(route, "application/javascript", resource("/count-submits.js"));
                case "/submitted" -> html(route, "<p>Submitted</p>");
                default -> route.fulfill(new Route.FulfillOptions().setStatus(404));
            }
        });
        context.route("https://elsewhere.test/**", route -> html(route, resource("/enroll.html")));
    }

    /** The server's real runner page, and whatever the test answers for the API. */
    static void serveServer(BrowserContext context, Consumer<Route> api) {
        Path statics = Path.of(System.getProperty("credcloud.static", "../src/main/resources/static"));
        context.route(SERVER + "/**", route -> {
            String path = URI.create(route.request().url()).getPath();
            if (path.startsWith("/runner/api/")) {
                api.accept(route);
            } else if (path.equals("/runner/home.html")) {
                html(route, read(statics.resolve("runner/home.html")));
            } else if (path.equals("/js/runner-home.js")) {
                fulfill(route, "application/javascript", read(statics.resolve("js/runner-home.js")));
            } else {
                route.fulfill(new Route.FulfillOptions().setStatus(404));
            }
        });
    }

    static void json(Route route, int status, String body) {
        route.fulfill(new Route.FulfillOptions().setStatus(status).setContentType("application/json").setBody(body));
    }

    private static void html(Route route, String body) {
        route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType("text/html")
                .setHeaders(Map.of("Content-Security-Policy", STRICT_CSP)).setBody(body));
    }

    private static void fulfill(Route route, String type, String body) {
        route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType(type).setBody(body));
    }

    private static String resource(String name) {
        try (InputStream in = FakeSites.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
