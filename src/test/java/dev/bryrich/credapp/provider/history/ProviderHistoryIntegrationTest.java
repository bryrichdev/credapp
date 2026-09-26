package dev.bryrich.credapp.provider.history;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Training and work history, saved through the provider's form and shown on their page. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProviderHistoryIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired JdbcTemplate jdbc;

    private User admin;
    private long provider;

    @BeforeEach
    void setUp() throws Exception {
        admin = register();
        String location = mvc.perform(post("/providers").with(signedIn(admin)).with(csrf())
                        .param("details.firstName", "Priya").param("details.lastName", "Shah"))
                .andReturn().getResponse().getRedirectedUrl();
        provider = Long.parseLong(location.replaceAll("\\D+", ""));
    }

    @Test
    void trainingAndWorkHistorySaveShowAndListGaps() throws Exception {
        mvc.perform(edit()
                        .param("training[0].trainingType", "RESIDENCY")
                        .param("training[0].institution", "University of Utah Health")
                        .param("training[0].specialty", "Family medicine")
                        .param("training[0].state", "ut")
                        .param("training[0].startDate", "2012-07-01")
                        .param("training[0].endDate", "2015-06-30")
                        .param("work[0].entryType", "JOB")
                        .param("work[0].employer", "Lakeside Clinic")
                        .param("work[0].position", "Staff physician")
                        .param("work[0].startDate", "2015-08-01")
                        .param("work[0].endDate", "2019-12-31")
                        .param("work[0].reasonForLeaving", "Relocated")
                        .param("work[1].entryType", "JOB")
                        .param("work[1].employer", "North Surgery")
                        .param("work[1].startDate", "2021-01-04"))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("SELECT state || ' ' || completed FROM provider_training WHERE provider_id = ?",
                String.class, provider)).isEqualTo("UT true");
        mvc.perform(get("/providers/" + provider).with(signedIn(admin)))
                .andExpect(content().string(containsString("University of Utah Health")))
                .andExpect(content().string(containsString("Residency")))
                .andExpect(content().string(containsString("Lakeside Clinic")))
                .andExpect(content().string(containsString("Gaps to explain")))
                .andExpect(content().string(containsString("Dec 2019 to Jan 2021 (12 months)")));

        // Explaining the gap with time away clears the warning.
        mvc.perform(edit()
                        .param("training[0].id", id("provider_training"))
                        .param("training[0].trainingType", "RESIDENCY")
                        .param("training[0].institution", "University of Utah Health")
                        .param("training[0].startDate", "2012-07-01")
                        .param("training[0].endDate", "2015-06-30")
                        .param("work[0].entryType", "GAP")
                        .param("work[0].startDate", "2020-01-01")
                        .param("work[0].endDate", "2020-12-31")
                        .param("work[0].gapExplanation", "Parental leave")
                        .param("work[1].entryType", "JOB")
                        .param("work[1].employer", "North Surgery")
                        .param("work[1].startDate", "2021-01-04")
                        .param("work[2].entryType", "JOB")
                        .param("work[2].employer", "Lakeside Clinic")
                        .param("work[2].startDate", "2015-08-01")
                        .param("work[2].endDate", "2019-12-31"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/providers/" + provider).with(signedIn(admin)))
                .andExpect(content().string(containsString("Parental leave")))
                .andExpect(content().string(not(containsString("Gaps to explain"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_training WHERE provider_id = ?", Long.class, provider))
                .as("the training row was kept, not duplicated").isEqualTo(1);
    }

    @Test
    void incompleteRowsAreSentBackWithTheReason() throws Exception {
        mvc.perform(edit()
                        .param("training[0].trainingType", "FELLOWSHIP")
                        .param("training[0].institution", "U of U")
                        .param("training[0].startDate", "2016-07-01")
                        .param("training[0].endDate", "2015-06-30")
                        .param("training[0].incomplete", "true")
                        .param("work[0].entryType", "JOB")
                        .param("work[0].startDate", "2015-08-01")
                        .param("work[1].entryType", "GAP")
                        .param("work[1].startDate", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("End date must be on or after the start date")))
                .andExpect(content().string(containsString("Give the reason the training was not completed")))
                .andExpect(content().string(containsString("Employer is required for a job")))
                .andExpect(content().string(containsString("Explain the time away")))
                .andExpect(content().string(containsString("Time away needs an end date")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_work_history WHERE provider_id = ?", Long.class, provider))
                .isZero();
    }

    private MockHttpServletRequestBuilder edit() {
        return post("/providers/" + provider + "/edit").with(signedIn(admin)).with(csrf())
                .param("details.firstName", "Priya").param("details.lastName", "Shah");
    }

    private String id(String table) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM " + table + " WHERE provider_id = ?", Long.class, provider));
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("History " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }
}
