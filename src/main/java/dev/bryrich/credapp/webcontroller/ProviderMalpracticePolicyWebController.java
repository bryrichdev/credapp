package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.MalpracticePolicyForm;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import dev.bryrich.credapp.service.MalpracticePolicyService;
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
 * Malpractice policies covering one provider. The owner is fixed at creation — a policy
 * covers exactly one provider or one group, and reassigning it is not an edit.
 */
@Controller
@RequestMapping("/providers/{providerId}/policies")
public class ProviderMalpracticePolicyWebController {

    private final MalpracticePolicyService policyService;
    private final ProviderService providerService;

    public ProviderMalpracticePolicyWebController(MalpracticePolicyService policyService,
                                                  ProviderService providerService) {
        this.policyService = policyService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newPolicy(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        MalpracticePolicyForm form = new MalpracticePolicyForm();
        form.setProviderId(providerId);
        model.addAttribute("form", form);
        return "provider/policy-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") MalpracticePolicyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/policy-form";
        }
        MalpracticePolicy saved = policyService.addForProvider(providerId,
                form.getPolicyNumber(), form.getCarrierName(), form.getTypeOfCoverage(),
                form.getEffectiveDate(), form.getSharedIndividual());
        policyService.update(saved.getId(), form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Policy added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        MalpracticePolicy policy = policyService.findById(id);
        addFormAttributes(providerId, model);
        model.addAttribute("form", MalpracticePolicyForm.from(policy));
        model.addAttribute("policyId", id);
        return "provider/policy-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") MalpracticePolicyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            model.addAttribute("policyId", id);
            return "provider/policy-form";
        }
        policyService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Policy updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        policyService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Policy deleted.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("scopes", CoverageScope.values());
    }
}
