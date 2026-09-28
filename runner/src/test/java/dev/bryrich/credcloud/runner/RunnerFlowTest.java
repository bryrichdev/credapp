package dev.bryrich.credcloud.runner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner end to end against a fake CredCloud and a fake portal: pairing in the CredCloud
 * tab, a fill job from Fill this page to Done, and a learn job from picking a box to saving.
 */
class RunnerFlowTest {

    private static final Gson GSON = new Gson();
    private static final String CODE = "ABCDE-FGH23";

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Runner runner;
    private final Deque<String> jobs = new ArrayDeque<>();
    private final List<String> posts = new ArrayList<>();

    @BeforeAll
    static void start() {
        playwright = Playwright.create();
        browser = FakeSites.launch(playwright);
    }

    @AfterAll
    static void stop() {
        playwright.close();
    }

    @BeforeEach
    void open() {
        context = browser.newContext();
        FakeSites.servePortal(context);
        FakeSites.serveServer(context, this::api);
        runner = new Runner(context, Config.inMemory(FakeSites.SERVER), Duration.ZERO);
    }

    @AfterEach
    void close() {
        context.close();
    }

    /** The fake CredCloud API. */
    private void api(Route route) {
        String path = route.request().url().replace(FakeSites.SERVER, "");
        String auth = route.request().headers().getOrDefault("authorization", "");
        if (path.equals("/runner/api/pair")) {
            boolean right = GSON.fromJson(route.request().postData(), JsonObject.class).get("code").getAsString().equals(CODE);
            FakeSites.json(route, right ? 200 : 401, right ? "{\"token\":\"t-1\"}" : "{\"error\":\"That code is wrong\"}");
        } else if (!auth.equals("Bearer t-1")) {
            FakeSites.json(route, 401, "{}");
        } else if (path.equals("/runner/api/jobs/next")) {
            String job = jobs.poll();
            if (job == null) {
                route.fulfill(new Route.FulfillOptions().setStatus(204));
            } else {
                FakeSites.json(route, 200, job);
            }
        } else {
            posts.add(path + " " + route.request().postData());
            FakeSites.json(route, 200, path.endsWith("/learned") ? "{\"revision\":2}" : "{}");
        }
    }

    @Test
    void pairsFromTheCredCloudTab_thenFillsAJobAndReportsIt() {
        Page home = stepUntil(() -> runner.current() == null && homeShows("Enter the code"));
        home.locator("#runner-code").fill(CODE);
        home.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Connect")).click();

        jobs.add(job(7, "fill", List.of(
                        FillerTest.field("First name", "label", "First name *", "text"),
                        FillerTest.field("State", "label", "State", "select"),
                        FillerTest.field("DEA", "label", "DEA number", "text")),
                List.of(new Model.Answer(0, "Jane"), new Model.Answer(1, "UT"), new Model.Answer(2, "AB1234567"))));
        stepUntil(() -> runner.current() != null);
        Page portal = runner.current().page();
        Locator panel = portal.locator("credcloud-runner");
        stepUntil(() -> panel.getByText("Jane Doe").count() == 1);

        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Fill this page")).click();
        stepUntil(() -> panel.getByText("Filled 2 boxes on this page").count() == 1);
        assertThat(portal.inputValue("#first")).isEqualTo("Jane");
        assertThat(portal.inputValue("#state")).isEqualTo("UT");

        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Done")).click();
        stepUntil(() -> runner.current() == null);
        assertThat(posts).singleElement().asString()
                .startsWith("/runner/api/jobs/7/filled ")
                .contains("\"filled\":[\"First name\",\"State\"]")
                .contains("\"missed\":[\"DEA\"]");
        assertThat(panel.getByText("submit it yourself").count()).isEqualTo(1);
        assertThat(portal.evaluate("window.submitted")).isEqualTo(0);
        assertThat(portal.isClosed()).as("left open for her to check and submit").isFalse();
    }

    @Test
    void learnsBoxesSheClicksAndSavesThemAsANewVersion() {
        paired();
        jobs.add(job(8, "learn", List.of(FillerTest.field("Old box", "css", "#gone", "text")), List.of()));
        stepUntil(() -> runner.current() != null);
        Page portal = runner.current().page();
        Locator panel = portal.locator("credcloud-runner");
        stepUntil(() -> panel.getByText("1 boxes so far").count() == 1);

        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("✕")).click();
        stepUntil(() -> panel.getByText("0 boxes so far").count() == 1);
        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Pick a box on the page")).click();
        stepUntil(() -> panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Stop picking")).count() == 1);

        portal.locator("#first").click();
        stepUntil(() -> panel.getByText("What goes in this box?").count() == 1);
        panel.locator("select").first().selectOption("provider.first_name");
        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add")).click();
        stepUntil(() -> panel.getByText("1 boxes so far").count() == 1);

        portal.locator("button[type=submit]").click();
        stepUntil(() -> panel.getByText("isn't a box").count() == 1);
        assertThat(portal.evaluate("window.submitted")).as("clicks go to the runner while picking").isEqualTo(0);

        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save template")).click();
        stepUntil(() -> runner.current() == null);
        assertThat(posts).singleElement().asString().startsWith("/runner/api/jobs/8/learned ");
        JsonObject saved = GSON.fromJson(posts.getFirst().substring(posts.getFirst().indexOf(' ') + 1), JsonObject.class);
        JsonObject field = saved.getAsJsonArray("fields").get(0).getAsJsonObject();
        assertThat(saved.getAsJsonArray("fields")).hasSize(1);
        assertThat(field.get("label").getAsString()).isEqualTo("First name");
        assertThat(field.get("by").getAsString()).isEqualTo("label");
        assertThat(field.get("locator").getAsString()).isEqualTo("First name *");
        assertThat(field.get("source").getAsString()).isEqualTo("provider.first_name");
        assertThat(field.get("page").getAsString()).isEqualTo("/enroll");
        assertThat(panel.getByText("Saved as version 2").count()).isEqualTo(1);
    }

    @Test
    void wontTypeIntoAPageThatIsntThePortal() {
        paired();
        jobs.add(job(9, "fill", List.of(FillerTest.field("First name", "label", "First name *", "text")),
                List.of(new Model.Answer(0, "Jane"))));
        stepUntil(() -> runner.current() != null);
        Page portal = runner.current().page();
        stepUntil(() -> portal.locator("credcloud-runner").getByText("Jane Doe").count() == 1);

        portal.navigate("https://elsewhere.test/enroll");
        Locator panel = portal.locator("credcloud-runner");
        stepUntil(() -> panel.getByText("Jane Doe").count() == 1);
        panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Fill this page")).click();
        stepUntil(() -> panel.getByText("isn't on the portal").count() == 1);
        assertThat(portal.inputValue("#first")).isEmpty();
    }

    /** A job as the server sends it, for the fake portal. */
    private static String job(long id, String kind, List<Model.Field> fields, List<Model.Answer> answers) {
        boolean fill = kind.equals("fill");
        return GSON.toJson(new Model.Job(id, kind, 3, "Enrollment", "Example Payer", FakeSites.PORTAL + "/enroll", 1,
                fields, fill ? "Jane Doe" : null, answers,
                fill ? List.of() : List.of(new Model.Source("provider.first_name", "Provider / First name"),
                        new Model.Source("provider.npi", "Provider / NPI")),
                fill ? Map.of() : Map.of("AS_SAVED", "As saved", "DIGITS", "Digits only")));
    }

    private void paired() {
        Page home = stepUntil(() -> homeShows("Enter the code"));
        home.locator("#runner-code").fill(CODE);
        home.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Connect")).click();
        stepUntil(() -> homeShows("Waiting for a job"));
    }

    private boolean homeShows(String text) {
        return context.pages().stream().anyMatch(p -> p.url().endsWith(Bridge.HOME)
                && p.locator("#runner-status").textContent().contains(text));
    }

    /** Steps the runner until the condition holds, as its loop would. */
    private Page stepUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            runner.step();
            Page home = context.pages().getFirst();
            home.waitForTimeout(100);
            if (condition.getAsBoolean()) {
                return home;
            }
        }
        throw new AssertionError("Timed out waiting for the runner");
    }
}
