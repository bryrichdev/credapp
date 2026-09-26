package dev.bryrich.credapp.application;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.registration.*;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.*;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PdfApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    private User admin;
    private long provider, payer;

    @BeforeEach
    void setup() throws Exception {
        admin = register();
        String path = mvc.perform(post("/providers").with(as(admin)).with(csrf())
                .param("details.firstName", "Priya").param("details.lastName", "Shah").param("details.npi", "1234567890")
                .param("details.ssn", "123456789")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        provider = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        payer = jdbc.queryForObject("INSERT INTO payers (user_group_id, name) VALUES (?, 'Example Payer') RETURNING id", Long.class, admin.getUserGroupId());
    }
    @Test
    void payerTemplateToReviewedEncryptedPdfAndRepeatGenerationUsesTheSameDocument() throws Exception {
        long template = template();
        mvc.perform(get("/payers/" + payer).with(as(admin))).andExpect(content().string(containsString("PDF templates")));
        mvc.perform(get("/providers/" + provider + "/applications").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Example Payer / Application")));
        long run = start(template);
        mvc.perform(get("/applications/" + run).with(as(admin))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Priya Shah")))
                .andExpect(content().string(containsString("Required answer is missing")));
        mvc.perform(post("/applications/" + run).with(as(admin)).with(csrf()).param("action", "generate").param("reviewed", "yes")
                .param("value0", "Priya Shah").param("value1", "1234567890")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Complete required answers")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE provider_id = ?", Long.class, provider)).isZero();
        generate(run);
        long document = jdbc.queryForObject("SELECT document_id FROM application_runs WHERE id = ?", Long.class, run);
        byte[] pdf = mvc.perform(get("/documents/" + document).with(as(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getDocumentCatalog().getAcroForm().getField("provider_name").getValueAsString()).isEqualTo("Priya Shah");
            assertThat(doc.getDocumentCatalog().getAcroForm().getField("practice_city").getValueAsString()).isEqualTo("Grand Rapids");
        }
        mvc.perform(get("/applications/" + run).with(as(admin))).andExpect(content().string(containsString("Download PDF")));
        generate(run);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE provider_id = ?", Long.class, provider)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_access_log WHERE document_id = ?", Long.class, document)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_access_log WHERE run_id = ? AND action = 'generate'", Long.class, run)).isEqualTo(1);
        assertThat(new String(jdbc.queryForObject("SELECT field_values FROM application_runs WHERE id = ?", byte[].class, run), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("Priya Shah", "123456789");
        assertThat(jdbc.queryForObject("SELECT content FROM documents WHERE id = ?", byte[].class, document)).isNotEqualTo(pdf);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ssn_access_log WHERE subject_id = ? AND subject_type = 'provider'", Long.class, provider)).isEqualTo(1);
    }
    @Test
    void workspaceIsolationReadOnlyAndCsrfAreEnforcedAcrossTheWorkflow() throws Exception {
        long template = template(), run = start(template);
        User outsider = register();
        for (String path : new String[]{"/payers/" + payer + "/templates", "/application-templates/" + template + "/edit", "/applications/" + run, "/providers/" + provider + "/applications"})
            mvc.perform(get(path).with(as(outsider))).andExpect(status().isNotFound());
        mvc.perform(post("/applications/" + run).with(as(outsider)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/providers/" + provider + "/applications").param("templateId", "" + template).with(as(outsider)).with(csrf())).andExpect(status().isNotFound());
        User reader = users.createInGroup(UUID.randomUUID() + "@example.com", "test-password-1234", "Reader", Role.READONLY, admin.getUserGroupId());
        mvc.perform(get("/applications/" + run).with(as(reader))).andExpect(status().isOk()).andExpect(content().string(not(containsString("Generate PDF"))));
        mvc.perform(post("/applications/" + run).with(as(reader)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/application-templates/" + template + "/edit").with(as(reader))).andExpect(status().isForbidden());
        mvc.perform(post("/applications/" + run).with(as(admin))).andExpect(status().isForbidden());
    }
    @Test
    void draftsKeepTheirMappingAndDataWhenTheProviderOrTemplateChanges() throws Exception {
        long template = template(), run = start(template);
        jdbc.update("UPDATE providers SET first_name = 'Changed' WHERE id = ?", provider);
        mvc.perform(post("/application-templates/" + template).with(as(admin)).with(csrf()).param("revision", "2").param("source0", "provider.last_name"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/applications/" + run).with(as(admin))).andExpect(content().string(containsString("Priya Shah")))
                .andExpect(content().string(containsString("Template revision 2")));
        mvc.perform(post("/application-templates/" + template).with(as(admin)).with(csrf()).param("revision", "2"))
                .andExpect(content().string(containsString("changed in another tab")));
    }
    @Test
    void requiresReviewAndKeepsAnswersOnValidationFailure() throws Exception {
        long run = start(template());
        mvc.perform(post("/applications/" + run).with(as(admin)).with(csrf()).param("action", "generate").param("value0", "Corrected name"))
                .andExpect(content().string(containsString("Confirm that you reviewed")))
                .andExpect(content().string(containsString("Corrected name")));
        assertThat(jdbc.queryForObject("SELECT status FROM application_runs WHERE id = ?", String.class, run)).isEqualTo("draft");
    }
    @Test
    void providerSsnIsWriteOnlyAndBlankEditsPreserveIt() throws Exception {
        String stored = jdbc.queryForObject("SELECT ssn FROM providers WHERE id = ?", String.class, provider);
        assertThat(stored).doesNotContain("123456789");
        mvc.perform(get("/providers/" + provider + "/edit").with(as(admin)))
                .andExpect(content().string(not(containsString("value=\"123456789\""))));
        mvc.perform(post("/providers/" + provider + "/edit").with(as(admin)).with(csrf()).param("details.firstName", "Priya")
                .param("details.lastName", "Shah").param("details.ssn", "")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/providers/" + provider + "/ssn").with(as(admin)).with(csrf())).andExpect(jsonPath("$.ssn").value("123456789"));
    }
    @Test
    void formatsShapeSavedValuesAndTheSsnStaysHiddenUntilShown() throws Exception {
        long template = template();
        mvc.perform(post("/application-templates/" + template).with(as(admin)).with(csrf()).param("revision", "2")
                .param("source0", "provider.full_name").param("format0", "UPPER")
                .param("source2", "provider.ssn").param("format2", "SSN")).andExpect(status().is3xxRedirection());
        long run = start(template);
        mvc.perform(get("/applications/" + run).with(as(admin)))
                .andExpect(content().string(containsString("value=\"PRIYA SHAH\"")))
                .andExpect(content().string(containsString("type=\"password\" autocomplete=\"off\" data-secret name=\"value2\" value=\"123-45-6789\"")));
        mvc.perform(post("/application-templates/" + template).with(as(admin)).with(csrf()).param("revision", "3")
                .param("format0", "NOT_A_FORMAT")).andExpect(content().string(containsString("Choose a listed format")));
    }
    @Test
    void anSsnTypedWithDashesIsSavedAsDigits() throws Exception {
        mvc.perform(post("/providers/" + provider + "/edit").with(as(admin)).with(csrf()).param("details.firstName", "Priya")
                .param("details.lastName", "Shah").param("details.ssn", "987-65-4321")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/providers/" + provider + "/ssn").with(as(admin)).with(csrf())).andExpect(jsonPath("$.ssn").value("987654321"));
    }
    @Test
    void aTemplateWithFormattingScriptsIsStoredWithoutThem() throws Exception {
        byte[] scripted = PdfApplicationEngineTest.withScripts(TestApplicationPdf.bytes());
        String path = mvc.perform(multipart("/payers/" + payer + "/templates").file(new MockMultipartFile("file", "acrobat.pdf", "application/pdf", scripted))
                .param("name", "Acrobat form").with(as(admin)).with(csrf())).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(path.split("/")[2]);
        mvc.perform(get("/application-templates/" + id + "/edit").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Print form"))));
    }
    private long template() throws Exception {
        String path = mvc.perform(multipart("/payers/" + payer + "/templates").file(new MockMultipartFile("file", "blank.pdf", "application/pdf", TestApplicationPdf.bytes()))
                .param("name", "Application").with(as(admin)).with(csrf())).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(path.split("/")[2]);
        mvc.perform(get(path).with(as(admin))).andExpect(status().isOk()).andExpect(content().string(containsString("Answer source")));
        mvc.perform(post("/application-templates/" + id).with(as(admin)).with(csrf()).param("revision", "1")
                .param("source0", "provider.full_name").param("source1", "provider.npi").param("source2", "provider.ssn")
                .param("source3", "location.city").param("default6", "Initial")).andExpect(status().is3xxRedirection());
        return id;
    }
    private long start(long template) throws Exception {
        String path = mvc.perform(post("/providers/" + provider + "/applications").with(as(admin)).with(csrf()).param("templateId", "" + template))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
    }
    private void generate(long run) throws Exception {
        mvc.perform(post("/applications/" + run).with(as(admin)).with(csrf()).param("action", "generate").param("reviewed", "yes")
                .param("value0", "Priya Shah").param("value1", "1234567890").param("value2", "123456789")
                .param("value3", "Grand Rapids").param("value4", "Reviewed").param("value5", "Yes").param("value6", "Initial"))
                .andExpect(redirectedUrl("/applications/" + run));
    }
    private User register() {
        var form = new RegistrationForm(); form.setEmail(UUID.randomUUID()+"@example.com"); form.setFullName("Admin"); form.setRole(Role.ADMIN);
        form.setPassword("test-password-1234"); form.setConfirmPassword("test-password-1234"); form.setGroupName("PDF test " + UUID.randomUUID()); return registration.register(form);
    }
    private static RequestPostProcessor as(User account) { return user(new CredAppUserDetails(account)); }
}
