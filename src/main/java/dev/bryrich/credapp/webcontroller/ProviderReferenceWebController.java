package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.ProviderReferenceForm;
import dev.bryrich.credapp.entity.ProviderReference;
import dev.bryrich.credapp.service.ProviderReferenceService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Peer references, nested under the provider who listed them. */
@Controller
@RequestMapping("/providers/{providerId}/references")
public class ProviderReferenceWebController {

    private final ProviderReferenceService referenceService;
    private final ProviderService providerService;

    public ProviderReferenceWebController(ProviderReferenceService referenceService,
                                          ProviderService providerService) {
        this.referenceService = referenceService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newReference(@PathVariable Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("form", new ProviderReferenceForm());
        return "provider/reference-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") ProviderReferenceForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(providerId));
            return "provider/reference-form";
        }
        referenceService.addReference(providerId, form.toEntity());
        redirectAttributes.addFlashAttribute("message", "Reference added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        ProviderReference reference = referenceService.findByIdAndProviderId(id, providerId);
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("form", ProviderReferenceForm.from(reference));
        model.addAttribute("referenceId", id);
        return "provider/reference-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") ProviderReferenceForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(providerId));
            model.addAttribute("referenceId", id);
            return "provider/reference-form";
        }
        referenceService.update(id, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Reference updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        referenceService.delete(id, providerId);
        redirectAttributes.addFlashAttribute("message", "Reference deleted.");
        return "redirect:/providers/" + providerId;
    }
}
