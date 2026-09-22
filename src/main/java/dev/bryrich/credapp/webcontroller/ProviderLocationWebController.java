package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.ProviderLocationForm;
import dev.bryrich.credapp.entity.ProviderLocation;
import dev.bryrich.credapp.entity.enums.PcpScp;
import dev.bryrich.credapp.service.ProviderLocationService;
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
 * Where a provider practises. The location dropdown offers only locations of groups the
 * provider already belongs to, which is also what the database will accept.
 */
@Controller
@RequestMapping("/providers/{providerId}/locations")
public class ProviderLocationWebController {

    private final ProviderLocationService providerLocationService;
    private final ProviderService providerService;

    public ProviderLocationWebController(ProviderLocationService providerLocationService,
                                         ProviderService providerService) {
        this.providerLocationService = providerLocationService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newLocation(@PathVariable Long providerId, Model model) {
        addFormAttributes(providerId, model);
        model.addAttribute("form", new ProviderLocationForm());
        return "provider/location-form";
    }

    @PostMapping
    public String create(@PathVariable Long providerId,
                         @Valid @ModelAttribute("form") ProviderLocationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            return "provider/location-form";
        }
        providerLocationService.assign(form.getLocationId(), providerId, form.getPcpScp());
        redirectAttributes.addFlashAttribute("message", "Location added.");
        return "redirect:/providers/" + providerId;
    }

    @GetMapping("/{locationId}/edit")
    public String edit(@PathVariable Long providerId,
                       @PathVariable Long locationId,
                       Model model) {
        ProviderLocation assignment = providerLocationService.findOne(locationId, providerId);
        addFormAttributes(providerId, model);
        model.addAttribute("form", ProviderLocationForm.from(assignment));
        model.addAttribute("assignedLocationId", locationId);
        return "provider/location-form";
    }

    @PostMapping("/{locationId}/edit")
    public String update(@PathVariable Long providerId,
                         @PathVariable Long locationId,
                         @Valid @ModelAttribute("form") ProviderLocationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(providerId, model);
            model.addAttribute("assignedLocationId", locationId);
            return "provider/location-form";
        }
        providerLocationService.assign(locationId, providerId, form.getPcpScp());
        redirectAttributes.addFlashAttribute("message", "Location updated.");
        return "redirect:/providers/" + providerId;
    }

    @PostMapping("/{locationId}/delete")
    public String delete(@PathVariable Long providerId,
                         @PathVariable Long locationId,
                         RedirectAttributes redirectAttributes) {
        providerLocationService.unassign(locationId, providerId);
        redirectAttributes.addFlashAttribute("message", "Location removed.");
        return "redirect:/providers/" + providerId;
    }

    private void addFormAttributes(Long providerId, Model model) {
        model.addAttribute("provider", providerService.findById(providerId));
        model.addAttribute("assignableLocations",
                providerLocationService.findAssignableLocations(providerId));
        model.addAttribute("pcpScpOptions", PcpScp.values());
    }
}
