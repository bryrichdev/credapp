package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.ProviderTaxonomyForm;
import dev.bryrich.credapp.service.ProviderService;
import dev.bryrich.credapp.service.ProviderTaxonomyService;
import dev.bryrich.credapp.service.TaxonomyService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A provider's specialties. There is no edit screen: a row is the pairing of provider and
 * code, so changing the code is a remove and an add. The only mutable part is which row is
 * primary, and that has its own endpoint.
 */
@Controller
@RequestMapping("/providers/{providerId}/taxonomies")
public class ProviderTaxonomyWebController {

    private final ProviderTaxonomyService providerTaxonomyService;
    private final TaxonomyService taxonomyService;
    private final ProviderService providerService;

    public ProviderTaxonomyWebController(ProviderTaxonomyService providerTaxonomyService,
                                         TaxonomyService taxonomyService,
                                         ProviderService providerService) {
        this.providerTaxonomyService = providerTaxonomyService;
        this.taxonomyService = taxonomyService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newTaxonomy(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        model.addAttribute("form", new ProviderTaxonomyForm());
        return "provider/taxonomy-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") ProviderTaxonomyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/taxonomy-form";
        }
        providerTaxonomyService.assign(providerId, form.getCode(), form.isPrimary());
        redirectAttributes.addFlashAttribute("message", "Specialty added.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{code}/primary")
    public String makePrimary(@PathVariable Long providerId,
                              @PathVariable String code,
                              RedirectAttributes redirectAttributes) {
        providerTaxonomyService.assign(providerId, code, true);
        redirectAttributes.addFlashAttribute("message", "Primary specialty updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{code}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable String code,
                         RedirectAttributes redirectAttributes) {
        providerTaxonomyService.unassign(providerId, code);
        redirectAttributes.addFlashAttribute("message", "Specialty removed.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("taxonomies", taxonomyService.findAllForSelect());
    }
}
