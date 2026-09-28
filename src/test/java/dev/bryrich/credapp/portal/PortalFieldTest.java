package dev.bryrich.credapp.portal;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a runner may save as a portal template, and what it may not. */
class PortalFieldTest {

    private static final Set<String> SOURCES = Set.of("provider.first_name", "provider.npi");

    private static PortalField field(String by, String locator, String kind, String source, String format, String fallback) {
        return new PortalField("First name", by, locator, kind, source, format, fallback, "/enroll");
    }

    @Test
    void labelAndCssLocatorsForEachKindOfBoxAreAccepted() {
        assertThatCode(() -> PortalField.check(List.of(
                field("label", "First name", "text", "provider.first_name", "", ""),
                field("css", "#npi", "text", "provider.npi", "DIGITS", ""),
                field("label", "State", "select", "", "", "UT"),
                field("label", "Accepting new patients", "checkbox", "", "", "Yes"),
                field("css", "input[name=gender][value=F]", "radio", "", "", "F")), SOURCES))
                .doesNotThrowAnyException();
    }

    @Test
    void anythingTheRunnerCantFillOrTheAppCantAnswerIsRefused() {
        assertThatThrownBy(() -> PortalField.check(List.of(), SOURCES)).hasMessageContaining("at least one box");
        assertThatThrownBy(() -> PortalField.check(List.of(field("xpath", "//input", "text", "provider.npi", "", "")), SOURCES))
                .hasMessageContaining("Show it again");
        assertThatThrownBy(() -> PortalField.check(List.of(field("label", " ", "text", "provider.npi", "", "")), SOURCES))
                .hasMessageContaining("Show it again");
        assertThatThrownBy(() -> PortalField.check(List.of(field("css", "button[type=submit]", "button", "", "", "x")), SOURCES))
                .as("there is no kind of field that clicks").hasMessageContaining("isn't a box CredCloud can fill");
        assertThatThrownBy(() -> PortalField.check(List.of(field("label", "SSN", "text", "provider.password", "", "")), SOURCES))
                .hasMessageContaining("Choose listed data");
        assertThatThrownBy(() -> PortalField.check(List.of(field("label", "Plan", "text", "", "", "")), SOURCES))
                .hasMessageContaining("data or a fixed answer");
        assertThatThrownBy(() -> PortalField.check(List.of(field("label", "DOB", "text", "provider.npi", "ROMAN", "")), SOURCES))
                .hasMessageContaining("listed format");
        assertThatThrownBy(() -> PortalField.check(
                Collections.nCopies(PortalField.MAX_FIELDS + 1, field("label", "x", "text", "provider.npi", "", "")), SOURCES))
                .hasMessageContaining("up to " + PortalField.MAX_FIELDS);
    }

    @Test
    void portalAddressesMustBeHttps() {
        assertThat(PortalTemplateService.checkUrl(" https://portal.example.com/enroll ")).isEqualTo("https://portal.example.com/enroll");
        assertThatThrownBy(() -> PortalTemplateService.checkUrl("http://portal.example.com")).hasMessageContaining("https://");
        assertThatThrownBy(() -> PortalTemplateService.checkUrl("javascript:alert(1)")).hasMessageContaining("https://");
        assertThatThrownBy(() -> PortalTemplateService.checkUrl("")).hasMessageContaining("https://");
    }
}
