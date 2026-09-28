package dev.bryrich.credapp.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** How a connected browser is named on the account page. */
class BrowserNameTest {

    @Test
    void namesTheBrowserAndSystem() {
        assertThat(PortalWebController.browserName(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36"))
                .isEqualTo("Chrome on macOS");
        assertThat(PortalWebController.browserName(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141.0 Safari/537.36 Edg/141.0"))
                .isEqualTo("Edge on Windows");
        assertThat(PortalWebController.browserName(null)).isEqualTo("Browser");
    }
}
