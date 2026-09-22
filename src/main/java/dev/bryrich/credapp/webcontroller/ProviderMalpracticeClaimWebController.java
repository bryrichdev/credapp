package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.MalpracticeClaimForm;
import dev.bryrich.credapp.entity.MalpracticeClaim;
import dev.bryrich.credapp.service.MalpracticeClaimService;
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

/** Malpractice claims. The policy link is optional and lists only this provider's policies. */
@Controller
@RequestMapping("/providers/{providerId}/claims")
public class ProviderMalpracticeClaimWebController {

    private final MalpracticeClaimService claimService;
    private final MalpracticePolicyService policyService;
    private final ProviderService providerService;

    public ProviderMalpracticeClaimWebController(MalpracticeClaimService claimService,
                                                 MalpracticePolicyService policyService,
                                                 ProviderService providerService) {
        this.claimService = claimService;
        this.policyService = policyService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newClaim(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        model.addAttribute("form", new MalpracticeClaimForm());
        return "provider/claim-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") MalpracticeClaimForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/claim-form";
        }
        claimService.addClaim(providerId, form.toEntity(), form.getPolicyId());
        redirectAttributes.addFlashAttribute("message", "Claim added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        MalpracticeClaim claim = claimService.findByIdAndProviderId(id, providerId);
        addFormAttributes(providerId, model);
        model.addAttribute("form", MalpracticeClaimForm.from(claim));
        model.addAttribute("claimId", id);
        return "provider/claim-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") MalpracticeClaimForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            model.addAttribute("claimId", id);
            return "provider/claim-form";
        }
        claimService.update(id, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Claim updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        claimService.delete(id, providerId);
        redirectAttributes.addFlashAttribute("message", "Claim deleted.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("policies", policyService.findByProviderId(providerId));
    }
}
