package dev.bryrich.credapp.portal.remote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Frame;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Runs portal/portal-page.js, CredCloud's own script for a portal's pages, in one frame. It
 * fills boxes and says which box is at a point. Playwright runs it in the page's world without
 * the portal's content security policy getting in the way.
 */
final class PortalScript {

    static final Gson GSON = new Gson();
    private static final String SCRIPT = load();

    private PortalScript() {
    }

    /** Runs one request, such as {action: "pick", x, y}, and returns the script's answer. */
    static JsonObject run(Frame frame, Object request) {
        String reply = (String) frame.evaluate(SCRIPT, GSON.toJson(request));
        return GSON.fromJson(reply, JsonObject.class);
    }

    /** The last two labels of the host: portal.payer.com and login.payer.com are one portal. */
    static boolean samePortal(String url, String startUrl) {
        try {
            String host = URI.create(url).getHost();
            String start = URI.create(startUrl).getHost();
            return host != null && start != null && site(host).equals(site(start));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The path part of a page's address, which is what a taught box remembers as its page. */
    static String path(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String site(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.matches("[\\d.]+") || lower.contains(":") || !lower.contains(".")) {
            return lower;
        }
        String[] labels = lower.split("\\.");
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static String load() {
        try (InputStream in = PortalScript.class.getResourceAsStream("/portal/portal-page.js")) {
            if (in == null) {
                throw new IllegalStateException("portal/portal-page.js is missing from the classpath");
            }
            String file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Playwright needs the function itself first, without the comments above it.
            return file.substring(file.indexOf("(input) =>"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
