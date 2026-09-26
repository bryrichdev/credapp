package dev.bryrich.credapp.document;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.OnboardingImportService;
import dev.bryrich.credapp.onboarding.TestWorkbook;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uploading, downloading and deleting documents, and who may. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final byte[] PDF = "%PDF-1.7\nA copy of a DEA certificate\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired OnboardingImportService imports;
    @Autowired JdbcTemplate jdbc;

    private User admin;
    private long provider;
    private long group;

    @BeforeEach
    void setUp() {
        admin = register();
        as(admin);
        imports.importFile(TestWorkbook.fullPractice().bytes());
        SecurityContextHolder.clearContext();
        provider = jdbc.queryForObject("SELECT min(id) FROM providers WHERE user_group_id = ?", Long.class, admin.getUserGroupId());
        group = jdbc.queryForObject("SELECT min(id) FROM groups WHERE user_group_id = ?", Long.class, admin.getUserGroupId());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDocumentIsStoredEncryptedAndDownloadsAsItWasUploaded() throws Exception {
        mvc.perform(upload("/providers/" + provider + "/documents", "dea.pdf", PDF)
                        .param("type", "dea").param("title", "Utah").param("expirationDate", "2030-01-31")
                        .with(signedIn(admin)).with(csrf()))
                .andExpect(redirectedUrl("/providers/" + provider + "#documents"))
                .andExpect(flash().attribute("message", "Document uploaded."));

        long id = jdbc.queryForObject("SELECT id FROM documents WHERE provider_id = ?", Long.class, provider);
        byte[] stored = jdbc.queryForObject("SELECT content FROM documents WHERE id = ?", byte[].class, id);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("DEA certificate");

        mvc.perform(get("/providers/" + provider).with(signedIn(admin)))
                .andExpect(content().string(containsString("DEA certificate")))
                .andExpect(content().string(containsString("dea.pdf")))
                .andExpect(content().string(containsString("2030-01-31")))
                .andExpect(content().string(containsString("/documents/" + id)));

        byte[] downloaded = mvc.perform(get("/documents/" + id).with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("dea.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
        assertThat(jdbc.queryForObject("SELECT user_email FROM document_access_log WHERE document_id = ?",
                String.class, id)).isEqualTo(admin.getEmail());

        mvc.perform(post("/documents/" + id + "/delete").with(signedIn(admin)).with(csrf()))
                .andExpect(redirectedUrl("/providers/" + provider + "#documents"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_access_log WHERE document_id = ?", Long.class, id))
                .as("the access record outlives the document").isEqualTo(1);
    }

    @Test
    void onlyRealDocumentsOfAcceptedKindsAreKept() throws Exception {
        String path = "/providers/" + provider + "/documents";
        expectRejected(upload(path, "page.pdf", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
                .param("type", "cv"), "doesn't look like a PDF, PNG, JPEG, HEIC, TIFF or Word file");
        expectRejected(upload(path, "photo.svg", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8))
                .param("type", "photo_id"), "doesn't look like");
        expectRejected(upload(path, "big.pdf", bigPdf()).param("type", "cv"), "over 10 MB");
        expectRejected(upload(path, "cv.pdf", PDF), "Choose what kind of document this is");
        expectRejected(upload(path, "empty.pdf", new byte[0]).param("type", "cv"), "Choose a file to upload");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE provider_id = ?", Long.class, provider)).isZero();

        // Folders a browser includes in the name are dropped.
        mvc.perform(upload(path, "C:\\Users\\casey\\scan.png",
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3})
                        .param("type", "photo_id").with(signedIn(admin)).with(csrf()))
                .andExpect(flash().attribute("message", "Document uploaded."));
        assertThat(jdbc.queryForObject("SELECT file_name || ' ' || content_type FROM documents WHERE provider_id = ?",
                String.class, provider)).isEqualTo("scan.png image/png");
    }

    @Test
    void anotherGroupCantSeeOrTouchIt_andReadOnlyCanOnlyDownload() throws Exception {
        mvc.perform(upload("/groups/" + group + "/documents", "w9.pdf", PDF).param("type", "w9")
                .with(signedIn(admin)).with(csrf()));
        long id = jdbc.queryForObject("SELECT id FROM documents WHERE group_id = ?", Long.class, group);
        mvc.perform(get("/groups/" + group).with(signedIn(admin)))
                .andExpect(content().string(containsString("W-9")));

        User outsider = register();
        mvc.perform(get("/documents/" + id).with(signedIn(outsider))).andExpect(status().isNotFound());
        mvc.perform(post("/documents/" + id + "/delete").with(signedIn(outsider)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(upload("/providers/" + provider + "/documents", "cv.pdf", PDF).param("type", "cv")
                        .with(signedIn(outsider)).with(csrf()))
                .andExpect(flash().attribute("documentError", "That provider doesn't exist"));

        User reader = users.createInGroup(UUID.randomUUID() + "@example.com", PASSWORD, "Reader", Role.READONLY,
                admin.getUserGroupId());
        mvc.perform(get("/documents/" + id).with(signedIn(reader))).andExpect(status().isOk());
        mvc.perform(get("/groups/" + group).with(signedIn(reader)))
                .andExpect(content().string(not(containsString("Upload a document"))));
        mvc.perform(upload("/groups/" + group + "/documents", "w9.pdf", PDF).param("type", "w9")
                        .with(signedIn(reader)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/documents/" + id + "/delete").with(signedIn(reader)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void anExpiringDocumentShowsInTrackingUntilANewerOneReplacesIt() throws Exception {
        String soon = LocalDate.now().plusDays(10).toString();
        mvc.perform(upload("/providers/" + provider + "/documents", "dea-old.pdf", PDF).param("type", "dea")
                .param("expirationDate", soon).with(signedIn(admin)).with(csrf()));

        mvc.perform(get("/tracking").with(signedIn(admin)))
                .andExpect(content().string(containsString("DEA certificate")))
                .andExpect(content().string(containsString("/providers/" + provider + "#documents")));
        mvc.perform(get("/providers/" + provider).with(signedIn(admin)))
                .andExpect(content().string(containsString("Needs attention")));

        mvc.perform(upload("/providers/" + provider + "/documents", "dea-new.pdf", PDF).param("type", "dea")
                .param("expirationDate", LocalDate.now().plusYears(3).toString()).with(signedIn(admin)).with(csrf()));
        mvc.perform(get("/tracking").with(signedIn(admin)))
                .andExpect(content().string(not(containsString("DEA certificate"))));
    }

    private void expectRejected(MockMultipartHttpServletRequestBuilder request, String message) throws Exception {
        mvc.perform(request.with(signedIn(admin)).with(csrf()))
                .andExpect(redirectedUrl("/providers/" + provider + "#documents"))
                .andExpect(flash().attribute("documentError", containsString(message)));
    }

    private static MockMultipartHttpServletRequestBuilder upload(String path, String name, byte[] bytes) {
        return multipart(path).file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
    }

    private static byte[] bigPdf() {
        byte[] big = new byte[(int) DocumentService.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        return big;
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Documents " + UUID.randomUUID());
        return registration.register(form);
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }

    private static void as(User actor) {
        var principal = new CredAppUserDetails(actor);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
