package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.onboarding.xlsx.XlsxReader;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWorkbook;
import dev.bryrich.credapp.provider.ProviderRepository;
import dev.bryrich.credapp.registration.RegistrationForm;
import dev.bryrich.credapp.registration.RegistrationService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/** The import pages end to end: who gets in, preview then import, and a superuser onboarding for a group. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OnboardingWebIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final Pattern TOKEN = Pattern.compile("name=\"token\" value=\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserService users;
    @Autowired ProviderRepository providers;

    /** Spring Session keeps the real session in the database, keyed by this cookie. */
    private Cookie sessionCookie;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyAdminsGetTheImportPages() throws Exception {
        User admin = register("Lakeside");
        User coordinator = users.createAs(admin, email(), PASSWORD, "Coordinator", Role.COORDINATOR);

        perform(get("/admin/import").with(signedIn(coordinator))).andExpect(status().isForbidden());
        perform(multipart("/admin/import/preview").file(upload(smallPractice()))
                .with(signedIn(coordinator)).with(csrf())).andExpect(status().isForbidden());

        perform(get("/admin/import").with(signedIn(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Download template (.xlsx)")));
        perform(get("/providers").with(signedIn(coordinator)))
                .andExpect(content().string(not(containsString("/admin/import"))));
    }

    @Test
    void theTemplateDownloadsAsAWorkbook() throws Exception {
        MvcResult result = perform(get("/admin/import/template").with(signedIn(register("Lakeside"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", OnboardingController.XLSX.toString()))
                .andExpect(header().string("Content-Disposition", containsString(OnboardingController.TEMPLATE_FILE_NAME)))
                .andReturn();

        XlsxWorkbook workbook = XlsxReader.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertThat(workbook.sheets()).hasSize(OnboardingTemplate.SHEETS.size() + 1);
    }

    @Test
    void anAdminPreviewsThenImports() throws Exception {
        User admin = register("Lakeside");

        String page = perform(multipart("/admin/import/preview").file(upload(smallPractice()))
                        .with(signedIn(admin)).with(csrf()).with(inSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ready to import")))
                .andExpect(content().string(containsString("Shah, Priya (P1)")))
                .andReturn().getResponse().getContentAsString();
        assertThat(as(admin, () -> providers.count())).isZero();

        perform(post("/admin/import/confirm").param("token", token(page)).with(signedIn(admin)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/providers"))
                .andExpect(flash().attribute("message",
                        "Imported onboarding.xlsx: 1 group and 1 provider, with everything linked to them."));
        assertThat(as(admin, () -> providers.findByNpi("1111111111"))).isPresent();

        // The preview is used up: the same token doesn't import twice.
        perform(post("/admin/import/confirm").param("token", token(page)).with(signedIn(admin)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/admin/import"));
        assertThat(as(admin, () -> providers.count())).isEqualTo(1);
    }

    @Test
    void problemsAreListedAndThereIsNothingToConfirm() throws Exception {
        User admin = register("Lakeside");
        byte[] file = new TestWorkbook()
                .row(OnboardingTemplate.PROVIDERS, "Provider ID", "P1", "First name", "Priya", "NPI", "12345")
                .bytes();

        perform(multipart("/admin/import/preview").file(upload(file)).with(signedIn(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 problems to fix")))
                .andExpect(content().string(containsString("NPI must be exactly 10 digits")))
                .andExpect(content().string(containsString("Last name is required")))
                .andExpect(content().string(not(containsString("/admin/import/confirm"))));

        perform(multipart("/admin/import/preview").file(new MockMultipartFile("file", "providers.csv",
                        "text/csv", "a,b".getBytes())).with(signedIn(admin)).with(csrf()))
                .andExpect(content().string(containsString("Save the file as an Excel workbook (.xlsx)")));
    }

    @Test
    void aSuperuserImportsIntoTheGroupTheyAreViewing() throws Exception {
        User superuser = users.promoteToSuperuser(register("Home office").getId());
        User practiceAdmin = register("Lakeside");
        Long practice = practiceAdmin.getUserGroupId();

        perform(post("/admin/user-groups/" + practice + "/view").param("next", "import")
                .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/admin/import"));
        String page = perform(multipart("/admin/import/preview").file(upload(smallPractice()))
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(content().string(containsString("Import into Lakeside")))
                .andReturn().getResponse().getContentAsString();

        perform(post("/admin/import/confirm").param("token", token(page))
                .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/providers"));

        assertThat(as(practiceAdmin, () -> providers.findByNpi("1111111111"))).isPresent();
        assertThat(as(superuser, () -> providers.count())).isZero();
        // Other writes stay closed while viewing.
        perform(post("/providers").with(signedIn(superuser)).with(csrf()).with(inSession())
                .param("details.firstName", "Not").param("details.lastName", "Allowed"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPreviewCantBeImportedIntoADifferentGroup() throws Exception {
        User superuser = users.promoteToSuperuser(register("Home office").getId());
        Long practice = register("Lakeside").getUserGroupId();

        perform(post("/admin/user-groups/" + practice + "/view").param("next", "import")
                .with(signedIn(superuser)).with(csrf()).with(inSession()));
        String page = perform(multipart("/admin/import/preview").file(upload(smallPractice()))
                        .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andReturn().getResponse().getContentAsString();
        perform(post("/admin/user-groups/view/exit").with(signedIn(superuser)).with(csrf()).with(inSession()));

        perform(post("/admin/import/confirm").param("token", token(page))
                .with(signedIn(superuser)).with(csrf()).with(inSession()))
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attribute("uploadError", containsString("another user group")));
        assertThat(as(superuser, () -> providers.count())).isZero();
    }

    // ============ helpers ============

    private static byte[] smallPractice() {
        return new TestWorkbook()
                .row(OnboardingTemplate.GROUPS, "Group ID", "G1", "Legal business name", "Lakeside Clinic LLC",
                        "Tax ID", "123456789")
                .row(OnboardingTemplate.PROVIDERS, "Provider ID", "P1", "First name", "Priya", "Last name", "Shah",
                        "NPI", "1111111111")
                .row(OnboardingTemplate.GROUP_MEMBERS, "Provider ID", "P1", "Group ID", "G1")
                .row(OnboardingTemplate.LICENSES, "Provider ID", "P1", "State", "OH", "License number", "35.1",
                        "License type", "MD", "Expiration date", "2027-01-31")
                .bytes();
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "onboarding.xlsx", OnboardingController.XLSX.toString(), bytes);
    }

    private static String token(String page) {
        Matcher matcher = TOKEN.matcher(page);
        assertThat(matcher.find()).as("the page has an Import button").isTrue();
        return matcher.group(1);
    }

    private User register(String groupName) {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(email());
        form.setFullName("Test User");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName(groupName);
        return registration.register(form);
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private static RequestPostProcessor signedIn(User account) {
        return user(new CredAppUserDetails(account));
    }

    private RequestPostProcessor inSession() {
        return request -> {
            if (sessionCookie != null) {
                request.setCookies(sessionCookie);
            }
            return request;
        };
    }

    /** MockMvc that keeps the session cookie the app sets, so a later request can send it back. */
    private ResultActions perform(RequestBuilder request) throws Exception {
        ResultActions result = mvc.perform(request);
        Cookie issued = result.andReturn().getResponse().getCookie("SESSION");
        if (issued != null) {
            sessionCookie = issued;
        }
        return result;
    }

    private <T> T as(User actor, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new CredAppUserDetails(actor);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
