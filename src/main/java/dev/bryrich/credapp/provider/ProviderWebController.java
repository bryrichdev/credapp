package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.group.GroupService;
import dev.bryrich.credapp.group.location.GroupLocationRepository;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.license.LicenseService;
import dev.bryrich.credapp.license.LicenseStatus;
import dev.bryrich.credapp.malpractice.CoverageScope;
import dev.bryrich.credapp.malpractice.MalpracticeClaimService;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.provider.certification.CertificationService;
import dev.bryrich.credapp.provider.disclosure.ChargeClassification;
import dev.bryrich.credapp.provider.disclosure.ChargeStatus;
import dev.bryrich.credapp.provider.disclosure.CriminalChargeService;
import dev.bryrich.credapp.provider.location.PcpScp;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.location.ProviderLocationService;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilegeService;
import dev.bryrich.credapp.provider.privilege.PrivilegeStatus;
import dev.bryrich.credapp.provider.reference.ProviderReferenceService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnAccessService;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyService;
import dev.bryrich.credapp.taxonomy.TaxonomyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/providers")
public class ProviderWebController {

    private final ProviderService providerService;
    private final ProviderProfileService profileService;
    private final LicenseService licenseService;
    private final GroupProviderService groupProviderService;
    private final GroupService groupService;
    private final GroupLocationRepository groupLocationRepository;
    private final TaxonomyService taxonomyService;
    private final SsnAccessService ssnAccessService;
    private final ProviderTaxonomyService providerTaxonomyService;
    private final ProviderLocationService providerLocationService;
    private final CertificationService certificationService;
    private final ProviderReferenceService referenceService;
    private final HospitalPrivilegeService privilegeService;
    private final CriminalChargeService chargeService;
    private final MalpracticePolicyService policyService;
    private final MalpracticeClaimService claimService;

    public ProviderWebController(ProviderService providerService,
                                 ProviderProfileService profileService,
                                 LicenseService licenseService,
                                 GroupProviderService groupProviderService,
                                 GroupService groupService,
                                 GroupLocationRepository groupLocationRepository,
                                 TaxonomyService taxonomyService,
                                 SsnAccessService ssnAccessService,
                                 ProviderTaxonomyService providerTaxonomyService,
                                 ProviderLocationService providerLocationService,
                                 CertificationService certificationService,
                                 ProviderReferenceService referenceService,
                                 HospitalPrivilegeService privilegeService,
                                 CriminalChargeService chargeService,
                                 MalpracticePolicyService policyService,
                                 MalpracticeClaimService claimService) {
        this.providerService = providerService;
        this.profileService = profileService;
        this.licenseService = licenseService;
        this.groupProviderService = groupProviderService;
        this.groupService = groupService;
        this.groupLocationRepository = groupLocationRepository;
        this.taxonomyService = taxonomyService;
        this.ssnAccessService = ssnAccessService;
        this.providerTaxonomyService = providerTaxonomyService;
        this.providerLocationService = providerLocationService;
        this.certificationService = certificationService;
        this.referenceService = referenceService;
        this.privilegeService = privilegeService;
        this.chargeService = chargeService;
        this.policyService = policyService;
        this.claimService = claimService;
    }

    /**
     * Same contract as the owner endpoint. Nothing writes providers.ssn through the web
     * UI yet, so this reports nothing on file until a field for it exists.
     */
    @PostMapping("/{id}/ssn")
    @ResponseBody
    public Map<String, String> revealSsn(@AuthenticationPrincipal CredAppUserDetails principal,
                                         @PathVariable Long id,
                                         HttpServletRequest request) {
        String ssn = ssnAccessService.revealProviderSsn(principal.getUser(), id, clientIp(request));
        Map<String, String> body = new HashMap<>();
        body.put("ssn", ssn);
        return body;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String lastName,
                       Pageable pageable,
                       Model model) {
        Page<Provider> page = (lastName == null || lastName.isBlank())
                ? providerService.findAll(pageable)
                : providerService.search(lastName, pageable);

        model.addAttribute("page", page);
        model.addAttribute("lastName", lastName);
        return "provider/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Provider provider = providerService.findById(id);
        model.addAttribute("provider", provider);
        model.addAttribute("ssnOnFile", ssnAccessService.providerSsnOnFile(id));
        model.addAttribute("licenses", licenseService.findByProviderId(id));
        model.addAttribute("groups", groupProviderService.findGroups(id));
        model.addAttribute("taxonomies", providerTaxonomyService.findByProviderId(id));
        model.addAttribute("practiceLocations", providerLocationService.findByProviderId(id));
        model.addAttribute("certifications", certificationService.findByProviderId(id));
        model.addAttribute("references", referenceService.findByProviderId(id));
        model.addAttribute("privileges", privilegeService.findByProviderId(id));
        model.addAttribute("charges", chargeService.findByProviderId(id));
        model.addAttribute("policies", policyService.findByProviderId(id));
        model.addAttribute("claims", claimService.findByProviderId(id));
        return "provider/detail";
    }

    // ============ the one form: new and edit ============

    @GetMapping("/new")
    public String newProvider(Model model) {
        model.addAttribute("form", new ProviderProfileForm());
        addFormOptions(null, model);
        return "provider/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProviderProfileForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return save(null, form, binding, model, redirectAttributes);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("form", profileService.load(id));
        addFormOptions(id, model);
        return "provider/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ProviderProfileForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        providerService.findById(id);
        return save(id, form, binding, model, redirectAttributes);
    }

    private String save(Long id, ProviderProfileForm form, BindingResult binding,
                        Model model, RedirectAttributes redirectAttributes) {
        profileService.validate(id, form, binding);
        if (binding.hasErrors()) {
            binding.reject("form.invalid", "Some fields need attention. They're marked below.");
            addFormOptions(id, model);
            return "provider/form";
        }
        Provider saved;
        try {
            saved = profileService.save(id, form);
        } catch (DataIntegrityViolationException ex) {
            binding.reject("save.conflict",
                    "Nothing was saved. A policy or claim number is already on file for that carrier, "
                            + "or an item being removed is still referenced elsewhere.");
            addFormOptions(id, model);
            return "provider/form";
        }
        redirectAttributes.addFlashAttribute("message", id == null ? "Provider added." : "Provider saved.");
        return "redirect:/providers/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String deleteProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        providerService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Provider deleted.");
        return "redirect:/providers";
    }

    /** Pick-lists for every section, plus a blank row of each kind for the "Add" buttons. */
    private void addFormOptions(Long providerId, Model model) {
        if (providerId != null) {
            model.addAttribute("provider", providerService.findById(providerId));
        }
        model.addAttribute("sexes", Sex.values());
        model.addAttribute("groupOptions", groupService.findAllForSelect());
        model.addAttribute("locationOptions", groupLocationRepository.findAllWithGroup());
        model.addAttribute("taxonomyOptions", taxonomyService.findAllForSelect());
        model.addAttribute("colleagues", providerService.findAllForSelect().stream()
                .filter(p -> !p.getId().equals(providerId))
                .toList());
        model.addAttribute("outsidePolicies", profileService.findOutsidePolicies(providerId));
        model.addAttribute("pcpScpOptions", PcpScp.values());
        model.addAttribute("licenseStatuses", LicenseStatus.values());
        model.addAttribute("privilegeStatuses", PrivilegeStatus.values());
        model.addAttribute("scopes", CoverageScope.values());
        model.addAttribute("chargeClassifications", ChargeClassification.values());
        model.addAttribute("chargeStatuses", ChargeStatus.values());

        Map<String, Object> blank = new HashMap<>();
        blank.put("group", new ProviderGroupForm());
        blank.put("location", new ProviderLocationForm());
        blank.put("taxonomy", new ProviderTaxonomyForm());
        blank.put("license", new ProviderProfileForm.LicenseRow());
        blank.put("certification", new ProviderProfileForm.CertificationRow());
        blank.put("privilege", new ProviderProfileForm.PrivilegeRow());
        blank.put("policy", new ProviderProfileForm.PolicyRow());
        blank.put("claim", new ProviderProfileForm.ClaimRow());
        blank.put("reference", new ProviderProfileForm.ReferenceRow());
        blank.put("charge", new ProviderProfileForm.ChargeRow());
        model.addAttribute("blank", blank);
    }
}
