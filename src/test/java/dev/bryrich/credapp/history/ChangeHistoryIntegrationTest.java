package dev.bryrich.credapp.history;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroupWipeService;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The triggers record who changed what, and the history pages show it to the right people only. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChangeHistoryIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserGroupWipeService wipes;

    private User admin;
    private long provider;

    @BeforeEach
    void setUp() throws Exception {
        admin = register("Dana Admin");
        String location = mvc.perform(post("/providers").with(signedIn(admin)).with(csrf())
                        .param("details.firstName", "Priya").param("details.lastName", "Shah")
                        .param("details.phoneNumber", "801-555-0100"))
                .andReturn().getResponse().getRedirectedUrl();
        provider = Long.parseLong(location.replaceAll("\\D+", ""));
    }

    @Test
    void recordsWhoChangedWhatAndShowsIt() throws Exception {
        mvc.perform(edit().param("details.phoneNumber", "801-555-0199")
                        .param("licenses[0].state", "UT").param("licenses[0].licenseNumber", "12345-MD")
                        .param("licenses[0].licenseType", "MD").param("licenses[0].status", "ACTIVE")
                        .param("licenses[0].expirationDate", "2028-01-31"))
                .andExpect(status().is3xxRedirection());

        Map<String, Object> added = jdbc.queryForMap("""
                SELECT actor_id, actor_email FROM change_log
                WHERE table_name = 'providers' AND operation = 'I' AND provider_id = ?""", provider);
        assertThat(added).containsEntry("actor_id", admin.getId()).containsEntry("actor_email", admin.getEmail());
        assertThat(jdbc.queryForObject("""
                SELECT changes::text FROM change_log
                WHERE table_name = 'providers' AND operation = 'U' AND provider_id = ?""", String.class, provider))
                .as("only the phone changed; timestamps aren't history")
                .isEqualTo("{\"phone_number\": {\"to\": \"801-555-0199\", \"from\": \"801-555-0100\"}}");

        mvc.perform(get("/providers/" + provider + "/history").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Provider details")))
                .andExpect(content().string(containsString("Phone number")))
                .andExpect(content().string(containsString("801-555-0100")))
                .andExpect(content().string(containsString("801-555-0199")))
                .andExpect(content().string(containsString("UT 12345-MD")))
                .andExpect(content().string(containsString("by Dana Admin")));
    }

    @Test
    void savingWithoutChangesRecordsNothing() throws Exception {
        mvc.perform(edit().param("details.phoneNumber", "801-555-0100")).andExpect(status().is3xxRedirection());
        long before = entries();
        mvc.perform(edit().param("details.phoneNumber", "801-555-0100")).andExpect(status().is3xxRedirection());
        assertThat(entries()).isEqualTo(before);
    }

    @Test
    void secretsAreRecordedAsChangedButNeverStored() {
        jdbc.update("UPDATE providers SET ssn = 'encrypted-bytes', caqh_secret_ref = 'ref' WHERE id = ?", provider);
        assertThat(jdbc.queryForObject("""
                SELECT changes::text || row_data::text FROM change_log
                WHERE table_name = 'providers' AND operation = 'U' AND provider_id = ?""", String.class, provider))
                .contains("\"ssn\": {\"masked\": true}", "\"caqh_secret_ref\": {\"masked\": true}")
                .doesNotContain("encrypted-bytes", "\"ref\"");
    }

    @Test
    void anotherGroupCannotSeeIt() throws Exception {
        User outsider = register("Other Admin");
        mvc.perform(get("/providers/" + provider + "/history").with(signedIn(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWipeLeavesNoHistoryBehind() {
        assertThat(entries()).isPositive();
        wipes.wipe(admin.getUserGroupId(), "test");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM change_log WHERE user_group_id = ?",
                Long.class, admin.getUserGroupId())).isZero();
    }

    private long entries() {
        return jdbc.queryForObject("SELECT count(*) FROM change_log WHERE provider_id = ?", Long.class, provider);
    }

    private MockHttpServletRequestBuilder edit() {
        return post("/providers/" + provider + "/edit").with(signedIn(admin)).with(csrf())
                .param("details.firstName", "Priya").param("details.lastName", "Shah");
    }

    private User register(String name) {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName(name);
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
