package dev.bryrich.credcloud.runner;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * CredCloud Runner. Opens Chrome with its own profile and fills payer portals for CredCloud.
 *
 * <pre>
 *   java -jar credcloud-runner.jar                                   production
 *   java -jar credcloud-runner.jar --server https://staging.credcloud.app
 * </pre>
 *
 * Uses the Chrome already installed; it downloads no browser. Close the Chrome window to stop.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String server = "https://credcloud.app";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--server") && i + 1 < args.length) {
                server = args[++i].replaceAll("/+$", "");
            } else {
                System.err.println("Usage: credcloud-runner [--server https://credcloud.app]");
                System.exit(2);
            }
        }
        Path home = Config.home();
        Config config = Config.load(home, server);
        var options = new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (Playwright playwright = Playwright.create(options)) {
            BrowserContext chrome = playwright.chromium().launchPersistentContext(home.resolve("chrome-profile"),
                    new BrowserType.LaunchPersistentContextOptions()
                            .setChannel("chrome")
                            .setHeadless(false)
                            .setViewportSize(null)
                            .setArgs(List.of("--start-maximized")));
            System.out.println("CredCloud Runner is running against " + server + ". Close Chrome to stop.");
            new Runner(chrome, config, Duration.ofSeconds(3)).run();
        }
    }
}
