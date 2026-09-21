package dev.bryrich.credapp.webcontroller;


import dev.bryrich.credapp.dto.LicenseForm;
import dev.bryrich.credapp.entity.enums.LicenseStatus;
import dev.bryrich.credapp.service.LicenseService;

import dev.bryrich.credapp.service.ProviderService;
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
        if (binding.hasErrors()) {
            addFormOptions(model);
            return "license/form";
        }
        licenseService.addLicense(form.getProviderId(), form.toEntity());
        redirectAttributes.addFlashAttribute("message", "License added.");
        return "redirect:/providers/" + form.getProviderId();
    }

    private void addFormOptions(Model model) {
        model.addAttribute("statuses", LicenseStatus.values());
        model.addAttribute("providers", providerService.findAllForSelect());
    }
}
