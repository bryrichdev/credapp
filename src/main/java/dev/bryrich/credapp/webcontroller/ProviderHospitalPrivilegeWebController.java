package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.HospitalPrivilegeForm;
import dev.bryrich.credapp.entity.HospitalPrivilege;
import dev.bryrich.credapp.entity.enums.PrivilegeStatus;
import dev.bryrich.credapp.service.HospitalPrivilegeService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Hospital privileges. The admitting-physician dropdown lists every other provider, and
 * the service rejects a provider naming themselves.
 */
@Controller
@RequestMapping("/providers/{providerId}/privileges")
public class ProviderHospitalPrivilegeWebController {

    private final HospitalPrivilegeService privilegeService;
    private final ProviderService providerService;

    public ProviderHospitalPrivilegeWebController(HospitalPrivilegeService privilegeService,
                                                  ProviderService providerService) {
        this.privilegeService = privilegeService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newPrivilege(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        model.addAttribute("form", new HospitalPrivilegeForm());
        return "provider/privilege-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") HospitalPrivilegeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/privilege-form";
        }
        privilegeService.addPrivilege(providerId, form.toEntity(), form.getAdmittingPhysicianId());
        redirectAttributes.addFlashAttribute("message", "Privilege added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        HospitalPrivilege privilege = privilegeService.findByIdAndProviderId(id, providerId);
        addFormAttributes(providerId, model);
        model.addAttribute("form", HospitalPrivilegeForm.from(privilege));
        model.addAttribute("privilegeId", id);
        return "provider/privilege-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") HospitalPrivilegeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            model.addAttribute("privilegeId", id);
            return "provider/privilege-form";
        }
        privilegeService.update(id, providerId, form.getAdmittingPhysicianId(), form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Privilege updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        privilegeService.delete(id, providerId);
        redirectAttributes.addFlashAttribute("message", "Privilege deleted.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("privilegeStatuses", PrivilegeStatus.values());
        model.addAttribute("colleagues", providerService.findAllForSelect().stream()
                .filter(candidate -> !candidate.getId().equals(providerId))
                .toList());
    }
}
