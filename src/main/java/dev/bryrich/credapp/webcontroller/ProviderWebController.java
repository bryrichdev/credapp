package dev.bryrich.credapp.webcontroller;


import dev.bryrich.credapp.dto.LicenseForm;
import dev.bryrich.credapp.dto.ProviderForm;
import dev.bryrich.credapp.entity.LicenseStatus;
import dev.bryrich.credapp.service.LicenseService;
import jakarta.validation.Valid;
import org.springframework.ui.Model;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.service.ProviderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/providers")
public class ProviderWebController {
    private final ProviderService providerService;
    private final LicenseService licenseService;

    public ProviderWebController(ProviderService providerService, LicenseService licenseService) {
        this.providerService = providerService;
        this.licenseService = licenseService;
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
    public String details(@PathVariable Long id, Model model) {
        Provider provider = providerService.findById(id);
        model.addAttribute("provider", provider);
        model.addAttribute("licenses", licenseService.findByProviderId(id));
        return "/provider/detail";
    }

    @GetMapping("/new")
    public String newProvider(Model model) {
        model.addAttribute("form", new ProviderForm());
        return "provider/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProviderForm form,
                         BindingResult binding) {
        if (binding.hasErrors()) {
            return "provider/form";
        }
        Provider saved = providerService.create(form.toEntity());
        return "redirect:/providers/" + saved.getId();
    }

    @GetMapping("/{id}/licenses/new")
    public String newLicense(@PathVariable Long id, Model model) {
        model.addAttribute("provider", providerService.findById(id));
        model.addAttribute("form", new LicenseForm());
        model.addAttribute("statuses", LicenseStatus.values());
        return "license/form";
    }

    @PostMapping("/{id}/licenses")
    public String createLicense(@PathVariable Long id,
                                @Valid @ModelAttribute("form") LicenseForm form,
                                BindingResult binding,
                                Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("statuses", LicenseStatus.values());
            return "license/form";
        }
        licenseService.addLicense(id, form.toEntity());
        return "redirect:/providers/" + id;
    }
}
