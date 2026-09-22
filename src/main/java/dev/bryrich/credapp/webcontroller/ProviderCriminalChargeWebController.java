package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.CriminalChargeForm;
import dev.bryrich.credapp.entity.CriminalCharge;
import dev.bryrich.credapp.entity.enums.ChargeClassification;
import dev.bryrich.credapp.entity.enums.ChargeStatus;
import dev.bryrich.credapp.service.CriminalChargeService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Disclosed criminal charges, nested under the provider who disclosed them. */
@Controller
@RequestMapping("/providers/{providerId}/charges")
public class ProviderCriminalChargeWebController {

    private final CriminalChargeService chargeService;
    private final ProviderService providerService;

    public ProviderCriminalChargeWebController(CriminalChargeService chargeService,
                                               ProviderService providerService) {
        this.chargeService = chargeService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newCharge(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        model.addAttribute("form", new CriminalChargeForm());
        return "provider/charge-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") CriminalChargeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/charge-form";
        }
        chargeService.addCharge(providerId, form.toEntity());
        redirectAttributes.addFlashAttribute("message", "Charge added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long providerId, @PathVariable Long id, Model model) {
        CriminalCharge charge = chargeService.findByIdAndProviderId(id, providerId);
        addFormAttributes(providerId, model);
        model.addAttribute("form", CriminalChargeForm.from(charge));
        model.addAttribute("chargeId", id);
        return "provider/charge-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") CriminalChargeForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            model.addAttribute("chargeId", id);
            return "provider/charge-form";
        }
        chargeService.update(id, providerId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Charge updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        chargeService.delete(id, providerId);
        redirectAttributes.addFlashAttribute("message", "Charge deleted.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("classifications", ChargeClassification.values());
        model.addAttribute("chargeStatuses", ChargeStatus.values());
    }
}
