package dev.bryrich.credapp.security;

import dev.bryrich.credapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The tab icon loads for everyone, the sign-in page included, before anyone is signed in. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FaviconIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void theIconsLoadWithoutSigningIn() throws Exception {
        mvc.perform(get("/favicon.ico")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/svg+xml")));
        mvc.perform(get("/apple-touch-icon.png")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/png")));
    }

    @Test
    void theSignInPageLinksThem() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("<link rel=\"icon\" href=\"/favicon.svg\" type=\"image/svg+xml\">")))
                .andExpect(content().string(containsString("<link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">")));
    }
}
