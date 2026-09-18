package dev.bryrich.credapp.webcontroller;


import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.service.LicenseService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/licenses")
public class LicenseWebController {
    private final LicenseService licenseService;

    public LicenseWebController(LicenseService licenseService) {
        this.licenseService = licenseService;
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

    @GetMapping("/expiring")
    public String expiring(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days, Model model) {
        List<License> licenses = licenseService.findExpiringSoon(days);
        model.addAttribute("licenses", licenses);
        model.addAttribute("days", days);
        model.addAttribute("today", LocalDate.now());
        return "license/expiring";
    }
}
