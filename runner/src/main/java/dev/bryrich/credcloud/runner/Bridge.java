package dev.bryrich.credcloud.runner;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The CredCloud tab. The runner keeps one tab open on the server's /runner/home.html and makes
 * every API call from inside it with fetch, so the requests come from real Chrome: Cloudflare's
 * bot checks treat them like any visitor, and on staging the Cloudflare Access sign-in in that
 * tab covers them too. The tab also shows her what the runner is doing, and it's where she
 * types the pairing code.
 */
final class Bridge {

    record Response(int status, String body) {
    }

    static final String HOME = "/runner/home.html";

    private static final String FETCH = """
            async ([method, path, token, body]) => {
              const headers = {};
              if (token) headers['Authorization'] = 'Bearer ' + token;
              if (body) headers['Content-Type'] = 'application/json';
              const response = await fetch(path, { method, headers, body: body || undefined,
                                                   credentials: 'same-origin', cache: 'no-store' });
              return { status: response.status, body: await response.text() };
            }""";

    private final BrowserContext context;
    private final String server;
    private final Queue<String> events = new ConcurrentLinkedQueue<>();
    private Page page;

    Bridge(BrowserContext context, String server) {
        this.context = context;
        this.server = server;
    }

    /** Opens the tab again if she closed it, and brings it back if it wandered off. */
    Page page() {
        if (page == null || page.isClosed()) {
            page = context.pages().stream().filter(p -> p.url().equals("about:blank")).findFirst()
                    .orElseGet(context::newPage);
            page.exposeBinding("credcloudRunnerHome", (source, args) -> {
                if (args.length == 1 && args[0] instanceof String json) {
                    events.add(json);
                }
                return null;
            });
            page.navigate(server + HOME);
        }
        return page;
    }

    /**
     * Whether the tab is on the runner's page. Right after opening, it may be on Cloudflare's
     * challenge or, for staging, the Access sign-in; the runner waits for her to get through.
     */
    boolean ready() {
        String url = page().url();
        return url.startsWith(server + HOME);
    }

    /** Events from the page, such as {"action":"paired","token":"..."}. */
    String nextEvent() {
        return events.poll();
    }

    Response call(String method, String path, String token, String body) {
        if (!ready()) {
            throw new PlaywrightException("The CredCloud tab isn't ready");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) page.evaluate(FETCH,
                List.of(method, path, token == null ? "" : token, body == null ? "" : body));
        return new Response(((Number) result.get("status")).intValue(), (String) result.get("body"));
    }

    /** Shows what the runner is doing: pairing, waiting, working, or offline. */
    void show(String state, String text) {
        try {
            if (ready()) {
                page.evaluate("s => window.runnerShow && window.runnerShow(s)", Map.of("state", state, "text", text));
            }
        } catch (PlaywrightException e) {
            // Mid-navigation; shown again on the next step.
        }
    }
}
