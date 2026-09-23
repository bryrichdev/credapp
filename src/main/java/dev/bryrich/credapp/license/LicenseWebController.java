package dev.bryrich.credapp.license;

import dev.bryrich.credapp.provider.ProviderService;

import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/licenses")
public class LicenseWebController {
    private final LicenseService licenseService;
    private final ProviderService providerService;

    public LicenseWebController(LicenseService licenseService, ProviderService providerService) {
        this.licenseService = licenseService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String providerName,
                       @PageableDefault(sort = "expirationDate") Pageable pageable,
                       Model model) {
        String term = (providerName == null) ? "" : providerName.trim();
        model.addAttribute("page", licenseService.searchByProviderName(term, pageable));
        model.addAttribute("providerName", providerName);
        return "license/list";
    }

    @GetMapping("/new")
    public String newLicense(Model model) {
        model.addAttribute("form", new LicenseForm());
        addFormOptions(model);
        return "license/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") LicenseForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (form.getProviderId() == null) {
            binding.rejectValue("providerId", "required", "Provider is required");
        }
        if (binding.hasErrors()) {
            addFormOptions(model);
            return "license/form";
        }
        licenseService.addLicense(form.getProviderId(), form.toEntity());
        redirectAttributes.addFlashAttribute("message", "License added.");
        return "redirect:/providers/" + form.getProviderId();
    }

    // Quick renewals from this list. Adding and removing licenses otherwise happens on the
    // provider form.

    @GetMapping("/{id}/edit")
    public String editLicense(@PathVariable Long id, Model model) {
        License license = licenseService.findById(id);
        LicenseForm form = LicenseForm.from(license);
        form.setProviderId(license.getProvider().getId());
        model.addAttribute("provider", license.getProvider());
        model.addAttribute("form", form);
        model.addAttribute("licenseId", id);
        model.addAttribute("statuses", LicenseStatus.values());
        return "license/form";
    }

    @PostMapping("/{id}/edit")
    public String updateLicense(@PathVariable Long id,
                                @Valid @ModelAttribute("form") LicenseForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        License license = licenseService.findById(id);
        Long providerId = license.getProvider().getId();
        if (binding.hasErrors()) {
            model.addAttribute("provider", license.getProvider());
            model.addAttribute("licenseId", id);
            model.addAttribute("statuses", LicenseStatus.values());
            return "license/form";
        }
        licenseService.update(id, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "License updated.");
        return "redirect:/licenses";
    }

    @PostMapping("/{id}/delete")
    public String deleteLicense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        License license = licenseService.findById(id);
        licenseService.delete(id, license.getProvider().getId());
        redirectAttributes.addFlashAttribute("message", "License deleted.");
        return "redirect:/licenses";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("statuses", LicenseStatus.values());
        model.addAttribute("providers", providerService.findAllForSelect());
    }
}
