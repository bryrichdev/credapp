package dev.bryrich.credapp.apicontroller;

import dev.bryrich.credapp.dto.ExpiringLicenseResponse;
import dev.bryrich.credapp.dto.LicenseResponse;
import dev.bryrich.credapp.service.LicenseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/licenses")
public class LicenseSearchController {

    private final LicenseService licenseService;

    public LicenseSearchController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping
    public Page<LicenseResponse> getAll(Pageable pageable) {
        return licenseService.findAll(pageable).map(LicenseResponse::from);
    }

    @GetMapping("/expiring")
    public List<ExpiringLicenseResponse> getExpiringSoon(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return licenseService.findExpiringSoon(days).stream()
                .map(ExpiringLicenseResponse::from)
                .toList();
    }
}