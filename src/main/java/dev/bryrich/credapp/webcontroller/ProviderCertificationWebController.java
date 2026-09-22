package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.CertificationForm;
import dev.bryrich.credapp.entity.Certification;
import dev.bryrich.credapp.service.CertificationService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Board certifications, nested under the provider they belong to. */
@Controller
@RequestMapping("/providers/{providerId}/certifications")
public class ProviderCertificationWebController {

    private final CertificationService certificationService;
    private final ProviderService providerService;

    public ProviderCertificationWebController(CertificationService certificationService,
                                              ProviderService providerService) {
        this.certificationService = certificationService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newCertification(@PathVariable Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("form", new CertificationForm());
        return "provider/certification-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") CertificationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(providerId));
            return "provider/certification-form";
        }
        certificationService.addCertification(providerId, form.toEntity());
        redirectAttributes.addFlashAttribute("message", "Certification added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        Certification certification = certificationService.findByIdAndProviderId(id, providerId);
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("form", CertificationForm.from(certification));
        model.addAttribute("certificationId", id);
        return "provider/certification-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") CertificationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(providerId));
            model.addAttribute("certificationId", id);
            return "provider/certification-form";
        }
        certificationService.update(id, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Certification updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        certificationService.delete(id, providerId);
        redirectAttributes.addFlashAttribute("message", "Certification deleted.");
        return "redirect:/providers/" + providerId;
    }
}
