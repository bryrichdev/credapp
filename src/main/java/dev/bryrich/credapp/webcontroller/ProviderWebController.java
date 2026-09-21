package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.LicenseForm;
import dev.bryrich.credapp.dto.ProviderForm;
import dev.bryrich.credapp.dto.ProviderGroupForm;
import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.enums.LicenseStatus;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.Sex;
import dev.bryrich.credapp.service.GroupProviderService;
import dev.bryrich.credapp.service.GroupService;
import dev.bryrich.credapp.service.LicenseService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/providers")
public class ProviderWebController {

    private final ProviderService providerService;
    private final LicenseService licenseService;
    private final GroupProviderService groupProviderService;
    private final GroupService groupService;

    public ProviderWebController(ProviderService providerService,
                                 LicenseService licenseService,
                                 GroupProviderService groupProviderService,
                                 GroupService groupService) {
        this.providerService = providerService;
        this.licenseService = licenseService;
        this.groupProviderService = groupProviderService;
        this.groupService = groupService;
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
    public String detail(@PathVariable Long id,
                         @RequestParam(name = "edit", defaultValue = "false") boolean edit,
                         Model model) {
        Provider provider = providerService.findById(id);
        model.addAttribute("provider", provider);
        model.addAttribute("licenses", licenseService.findByProviderId(id));
        model.addAttribute("groups", groupProviderService.findGroups(id));
        model.addAttribute("editing", edit);
        if (edit) {
            model.addAttribute("form", ProviderForm.from(provider));
            model.addAttribute("sexes", Sex.values());
        }
        return "provider/detail";
    }

    @PostMapping("/{id}/edit")
    public String updateProvider(@PathVariable Long id,
                                 @Valid @ModelAttribute("form") ProviderForm form,
                                 BindingResult binding,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("licenses", licenseService.findByProviderId(id));
            model.addAttribute("groups", groupProviderService.findGroups(id));
            model.addAttribute("editing", true);
            model.addAttribute("sexes", Sex.values());
            return "provider/detail";
        }
        providerService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Provider updated.");
        return "redirect:/providers/" + id;
    }

    @GetMapping("/new")
    public String newProvider(Model model) {
        model.addAttribute("form", new ProviderForm());
        model.addAttribute("sexes", Sex.values());
        model.addAttribute("groups", groupService.findAllForSelect());
        return "provider/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProviderForm form,
                         BindingResult binding,
                         Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("sexes", Sex.values());
            model.addAttribute("groups", groupService.findAllForSelect());
            return "provider/form";
        }
        Provider saved = providerService.create(form.toEntity());
        if (form.getGroupId() != null) {
            groupProviderService.assign(form.getGroupId(), saved.getId(), null);
        }
        return "redirect:/providers/" + saved.getId();
    }

    @GetMapping("/{id}/licenses/new")
    public String newLicense(@PathVariable Long id, Model model) {
        LicenseForm form = new LicenseForm();
        form.setProviderId(id);
        model.addAttribute("provider", providerService.findById(id));
        model.addAttribute("form", form);
        model.addAttribute("statuses", LicenseStatus.values());
        return "license/form";
    }

    @PostMapping("/{id}/licenses")
    public String createLicense(@PathVariable Long id,
                                @Valid @ModelAttribute("form") LicenseForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("statuses", LicenseStatus.values());
            return "license/form";
        }
        licenseService.addLicense(id, form.toEntity());
        redirectAttributes.addFlashAttribute("message", "License added.");
        return "redirect:/providers/" + id;
    }

    @GetMapping("/{providerId}/licenses/{licenseId}/edit")
    public String editLicense(@PathVariable Long providerId,
                              @PathVariable Long licenseId,
                              Model model) {
        License license = licenseService.findByIdAndProviderId(licenseId, providerId);
        LicenseForm form = LicenseForm.from(license);
        form.setProviderId(providerId);
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("form", form);
        model.addAttribute("statuses", LicenseStatus.values());
        model.addAttribute("licenseId", licenseId);
        return "license/form";
    }

    @PostMapping("/{providerId}/licenses/{licenseId}/edit")
    public String updateLicense(@PathVariable Long providerId,
                                @PathVariable Long licenseId,
                                @Valid @ModelAttribute("form") LicenseForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(providerId));
            model.addAttribute("statuses", LicenseStatus.values());
            model.addAttribute("licenseId", licenseId);
            return "license/form";
        }
        licenseService.update(licenseId, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "License updated.");
        return "redirect:/providers/" + providerId;
    }

    // ============ groups this provider belongs to ============

    @GetMapping("/{id}/groups/new")
    public String newProviderGroup(@PathVariable Long id, Model model) {
        model.addAttribute("provider", providerService.findById(id));
        model.addAttribute("form", new ProviderGroupForm());
        model.addAttribute("groups", groupService.findAllForSelect());
        return "provider/group-form";
    }

    @PostMapping("/{id}/groups")
    public String addProviderGroup(@PathVariable Long id,
                                   @Valid @ModelAttribute("form") ProviderGroupForm form,
                                   BindingResult binding,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("groups", groupService.findAllForSelect());
            return "provider/group-form";
        }
        groupProviderService.assign(form.getGroupId(), id, form.getEffectiveDate());
        redirectAttributes.addFlashAttribute("message", "Group added.");
        return "redirect:/providers/" + id;
    }

    @PostMapping("/{providerId}/groups/{groupId}/delete")
    public String removeProviderGroup(@PathVariable Long providerId,
                                      @PathVariable Long groupId,
                                      RedirectAttributes redirectAttributes) {
        groupProviderService.unassign(groupId, providerId);
        redirectAttributes.addFlashAttribute("message", "Group removed.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String deleteProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        providerService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Provider deleted.");
        return "redirect:/providers";
    }

    @PostMapping("/{providerId}/licenses/{licenseId}/delete")
    public String deleteLicense(@PathVariable Long providerId,
                                @PathVariable Long licenseId,
                                RedirectAttributes redirectAttributes) {
        licenseService.delete(licenseId, providerId);
        redirectAttributes.addFlashAttribute("message", "License deleted.");
        return "redirect:/providers/" + providerId;
    }
}
