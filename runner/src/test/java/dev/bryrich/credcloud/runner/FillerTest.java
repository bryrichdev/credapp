package dev.bryrich.credcloud.runner;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Filling the fake portal: every kind of box, and never a button. */
class FillerTest {

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

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
        page = context.newPage();
        page.navigate(FakeSites.PORTAL + "/enroll");
    }

    @AfterEach
    void close() {
        context.close();
    }

    static Model.Field field(String label, String by, String locator, String kind) {
        return new Model.Field(label, by, locator, kind, "", "", "", "/enroll");
    }

    @Test
    void fillsEachKindOfBox_andLeavesAButtonAlone() {
        List<Model.Field> fields = List.of(
                field("First name", "label", "First name *", "text"),
                field("NPI", "css", "#npi", "text"),
                field("State", "label", "State", "select"),
                field("Accepting new patients", "label", "Accepting new patients", "checkbox"),
                field("Female", "label", "Female", "radio"),
                field("Tax ID", "label", "Tax ID", "text"),
                field("Submit", "css", "button[type=submit]", "text"),
                field("Fax", "label", "Fax", "text"),
                field("Middle name", "css", "#first", "text"));
        List<String> answers = List.of("Jane", "1234567890", "Utah", "Yes", "Yes", "12-3456789", "anything",
                "801-555-0100", "");

        Filler.Report report = Filler.fill(page, fields, answers, Set.of());

        assertThat(report.filled()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(page.inputValue("#first")).isEqualTo("Jane");
        assertThat(page.inputValue("#npi")).isEqualTo("1234567890");
        assertThat(page.inputValue("#state")).isEqualTo("UT");
        assertThat(page.isChecked("input[name=accepting]")).isTrue();
        assertThat(page.isChecked("input[value=F]")).isTrue();
        assertThat(page.inputValue("input[name=tin]")).isEqualTo("12-3456789");
        assertThat(report.problems()).singleElement().asString().startsWith("Submit:").contains("isn't a box");
        assertThat(page.evaluate("window.submitted")).as("nothing was submitted").isEqualTo(0);
        assertThat(page.url()).isEqualTo(FakeSites.PORTAL + "/enroll");
    }

    @Test
    void boxesFilledOnAnEarlierPageAreLeftAlone_andNoUnticks() {
        page.check("input[name=accepting]");
        List<Model.Field> fields = List.of(field("First name", "label", "First name *", "text"),
                field("Accepting new patients", "label", "Accepting new patients", "checkbox"));

        Filler.Report report = Filler.fill(page, fields, List.of("Jane", "No"), Set.of(0));

        assertThat(report.filled()).containsExactly(1);
        assertThat(page.inputValue("#first")).isEmpty();
        assertThat(page.isChecked("input[name=accepting]")).isFalse();
    }

    @Test
    void onlyThePortalAJobWasSentForCounts() {
        assertThat(Filler.samePortal("https://portal.payer.com/enroll", "https://portal.payer.com/start")).isTrue();
        assertThat(Filler.samePortal("https://login.payer.com/sso", "https://portal.payer.com/start")).isTrue();
        assertThat(Filler.samePortal("https://payer.com.evil.test/enroll", "https://portal.payer.com/start")).isFalse();
        assertThat(Filler.samePortal("about:blank", "https://portal.payer.com/start")).isFalse();
    }
}
