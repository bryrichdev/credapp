package dev.bryrich.credapp.payer.enrollment;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupForm;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.GroupProfileService;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.payer.PayerContact;
import dev.bryrich.credapp.payer.PayerContactService;
import dev.bryrich.credapp.payer.PayerService;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderForm;
import dev.bryrich.credapp.provider.ProviderProfileForm;
import dev.bryrich.credapp.provider.ProviderProfileService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Provider and group enrollments with payers, and a group's account rep, from the form to every page. */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "credapp.security.ssn-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PayerEnrollmentIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired PayerService payers;
    @Autowired PayerContactService contacts;
    @Autowired GroupProfileService groupProfiles;
    @Autowired ProviderProfileService providerProfiles;
    @Autowired PayerEnrollmentService enrollments;

    private User admin;
    private Payer aetna;
    private Payer cigna;
    private PayerContact jane;
    private PayerContact bob;
    private Group lakeside;
    private Group north;

    @BeforeEach
    void setUp() {
        admin = register();
        as(() -> {
            aetna = payers.create(new Payer("Aetna"));
            cigna = payers.create(new Payer("Cigna"));
            jane = contacts.addContact(aetna.getId(), "Provider rep", c -> {
                c.setName("Jane Doe");
                c.setPhoneNumber("800-555-0100");
                c.setEmailAddress("jane.doe@aetna.test");
            });
            bob = contacts.addContact(cigna.getId(), "Network manager", c -> c.setName("Bob Ray"));
            lakeside = groupProfiles.save(null, group("Lakeside Clinic LLC"));
            north = groupProfiles.save(null, group("North Surgery PC"));
            return null;
        });
    }

    @Test
    void aGroupsEnrollmentsAndRepSaveFromTheFormAndShowEverywhere() throws Exception {
        mvc.perform(get("/groups/" + lakeside.getId() + "/edit").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-section=\"payers\"")))
                .andExpect(content().string(containsString("data-payer=\"" + aetna.getId() + "\"")))
                .andExpect(content().string(containsString("Jane Doe · Provider rep")));

        mvc.perform(groupForm(lakeside)
                        .param("payers[0].payerId", aetna.getId().toString())
                        .param("payers[0].status", "ACTIVE")
                        .param("payers[0].payerAssignedId", "G-123")
                        .param("payers[0].effectiveDate", "2024-01-01")
                        .param("payers[0].accountRepId", jane.getId().toString())
                        .param("payers[1].payerId", cigna.getId().toString())
                        .param("payers[1].status", "IN_PROGRESS")
                        .param("payers[1].submittedDate", "2025-06-01")
                        .param("payers[1].notes", "Waiting on the W-9"))
                .andExpect(redirectedUrl("/groups/" + lakeside.getId()));

        mvc.perform(get("/groups/" + lakeside.getId()).with(signedIn()))
                .andExpect(content().string(containsString("G-123")))
                .andExpect(content().string(containsString("Jane Doe · Provider rep")))
                .andExpect(content().string(containsString("tel:800-555-0100")))
                .andExpect(content().string(containsString("Waiting on the W-9")))
                .andExpect(content().string(containsString("In progress")));
        mvc.perform(get("/payers/" + aetna.getId()).with(signedIn()))
                .andExpect(content().string(containsString("Lakeside Clinic LLC")))
                .andExpect(content().string(containsString("jane.doe@aetna.test")));

        // A provider in the group sees the group's rep for that payer.
        Provider priya = as(() -> {
            ProviderProfileForm form = provider("Priya", "Shah");
            ProviderGroupForm membership = new ProviderGroupForm();
            membership.setGroupId(lakeside.getId());
            form.getGroups().add(membership);
            form.getPayers().add(providerRow(aetna, EnrollmentStatus.SUBMITTED));
            return providerProfiles.save(null, form);
        });
        mvc.perform(get("/providers/" + priya.getId()).with(signedIn()))
                .andExpect(content().string(containsString("Submitted")))
                .andExpect(content().string(containsString("Jane Doe · Provider rep")))
                .andExpect(content().string(containsString("for Lakeside Clinic LLC")));
        mvc.perform(get("/payers/" + aetna.getId()).with(signedIn()))
                .andExpect(content().string(containsString("Shah, Priya")));
    }

    @Test
    void theFormCatchesMistakesAndSavesNothing() throws Exception {
        mvc.perform(groupForm(lakeside)
                        .param("payers[0].payerId", aetna.getId().toString())
                        .param("payers[0].status", "ACTIVE")
                        .param("payers[0].accountRepId", bob.getId().toString())
                        .param("payers[1].payerId", aetna.getId().toString())
                        .param("payers[1].status", "NOT_STARTED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add the effective date for an active enrollment")))
                .andExpect(content().string(containsString("Pick one of this payer&#39;s contacts")))
                .andExpect(content().string(containsString("This payer is already listed")));

        assertThat(as(() -> enrollments.findForGroup(lakeside.getId()))).isEmpty();
    }

    @Test
    void aRepMustBePayerWideOrThisGroupsOwn() {
        PayerContact northsRep = as(() -> contacts.addGroupContact(aetna.getId(), north.getId(), "Account rep",
                c -> c.setName("Nora North")));
        PayerContact lakesidesRep = as(() -> contacts.addGroupContact(aetna.getId(), lakeside.getId(), "Account rep",
                c -> c.setName("Lee Lake")));

        assertThat(repErrors(lakeside, northsRep)).containsExactly(
                "That contact is tied to someone else; pick a payer-wide contact or one for this group");
        assertThat(repErrors(lakeside, lakesidesRep)).isEmpty();
        assertThat(repErrors(lakeside, jane)).isEmpty();
        // The pickers offer the same: payer-wide contacts, and this group's own.
        assertThat(as(() -> enrollments.repCandidates(lakeside.getId())))
                .extracting(PayerContact::getName).contains("Jane Doe", "Lee Lake").doesNotContain("Nora North");
    }

    @Test
    void losingTheRepContactClearsTheRepButKeepsTheEnrollment() {
        PayerContact lee = as(() -> contacts.addGroupContact(aetna.getId(), lakeside.getId(), "Account rep",
                c -> c.setName("Lee Lake")));
        saveGroupPayers(lakeside, groupRow(aetna, lee));
        saveGroupPayers(north, groupRow(aetna, jane));

        // Moved to another group: no longer this group's rep.
        as(() -> contacts.rescope(lee.getId(), aetna.getId(), north.getId(), null));
        assertThat(as(() -> enrollments.findForGroup(lakeside.getId()))).singleElement()
                .satisfies(e -> assertThat(e.getAccountRep()).isNull());

        // Deleted outright: the database clears it.
        as(() -> {
            contacts.delete(jane.getId(), aetna.getId());
            return null;
        });
        assertThat(as(() -> enrollments.findForGroup(north.getId()))).singleElement()
                .satisfies(e -> {
                    assertThat(e.getAccountRep()).isNull();
                    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.NOT_STARTED);
                });
    }

    @Test
    void editingUpdatesRowsInPlaceAndRemovedRowsAreDeleted() throws Exception {
        saveGroupPayers(lakeside, groupRow(aetna, null), groupRow(cigna, null));
        Long aetnaEnrollment = as(() -> enrollments.findForGroup(lakeside.getId())).stream()
                .filter(e -> e.getPayer().getId().equals(aetna.getId())).findFirst().orElseThrow().getId();

        mvc.perform(groupForm(lakeside)
                        .param("payers[0].payerId", aetna.getId().toString())
                        .param("payers[0].status", "DENIED")
                        .param("payers[0].notes", "Panel closed"))
                .andExpect(redirectedUrl("/groups/" + lakeside.getId()));

        assertThat(as(() -> enrollments.findForGroup(lakeside.getId()))).singleElement().satisfies(e -> {
            assertThat(e.getId()).isEqualTo(aetnaEnrollment);
            assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.DENIED);
            assertThat(e.getNotes()).isEqualTo("Panel closed");
        });
        mvc.perform(get("/payers/" + cigna.getId()).with(signedIn()))
                .andExpect(content().string(not(containsString("Lakeside Clinic LLC"))));
    }

    // ============ helpers ============

    private List<String> repErrors(Group group, PayerContact rep) {
        GroupProfileForm form = as(() -> groupProfiles.load(group.getId()));
        form.getPayers().add(groupRow(aetna, rep));
        var errors = new org.springframework.validation.BeanPropertyBindingResult(form, "form");
        as(() -> {
            groupProfiles.validate(group.getId(), form, errors);
            return null;
        });
        return errors.getFieldErrors().stream().map(e -> e.getDefaultMessage()).toList();
    }

    private void saveGroupPayers(Group group, GroupPayerForm... rows) {
        as(() -> {
            GroupProfileForm form = groupProfiles.load(group.getId());
            form.getPayers().addAll(List.of(rows));
            return groupProfiles.save(group.getId(), form);
        });
    }

    private static GroupPayerForm groupRow(Payer payer, PayerContact rep) {
        GroupPayerForm row = new GroupPayerForm();
        row.setPayerId(payer.getId());
        row.setAccountRepId(rep == null ? null : rep.getId());
        return row;
    }

    private static ProviderPayerForm providerRow(Payer payer, EnrollmentStatus status) {
        ProviderPayerForm row = new ProviderPayerForm();
        row.setPayerId(payer.getId());
        row.setStatus(status);
        row.setSubmittedDate(LocalDate.of(2025, 5, 1));
        return row;
    }

    private MockHttpServletRequestBuilder groupForm(Group group) {
        return post("/groups/" + group.getId() + "/edit").with(signedIn()).with(csrf())
                .param("details.lbn", group.getLbn())
                .param("details.taxId", group.getTaxId());
    }

    private static GroupProfileForm group(String name) {
        GroupProfileForm form = new GroupProfileForm();
        GroupForm details = new GroupForm();
        details.setLbn(name);
        details.setTaxId("123456789");
        form.setDetails(details);
        return form;
    }

    private static ProviderProfileForm provider(String first, String last) {
        ProviderProfileForm form = new ProviderProfileForm();
        ProviderForm details = new ProviderForm();
        details.setFirstName(first);
        details.setLastName(last);
        form.setDetails(details);
        return form;
    }

    private User register() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(UUID.randomUUID() + "@example.com");
        form.setFullName("Test Admin");
        form.setRole(Role.ADMIN);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        form.setGroupName("Enrollment test group");
        return registration.register(form);
    }

    private RequestPostProcessor signedIn() {
        return user(new CredAppUserDetails(admin));
    }

    private <T> T as(Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new CredAppUserDetails(admin);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
