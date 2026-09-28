package dev.bryrich.credapp.common;

import dev.bryrich.credapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The privacy policy opens without signing in, as the Chrome Web Store needs, and is linked from sign-in. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "credapp.contact-email=privacy@credcloud.test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrivacyPageIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void anyoneCanReadIt_andItSaysWhoToContact() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CredCloud for Chrome")))
                .andExpect(content().string(containsString("mailto:privacy@credcloud.test")));
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/privacy\"")));
    }
}
