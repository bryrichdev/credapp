package dev.bryrich.credapp.controller;

import dev.bryrich.credapp.dto.CreateLicenseRequest;
import dev.bryrich.credapp.dto.LicenseResponse;
import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.service.LicenseService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/api/providers/{providerId}/licenses")
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping
    public List<LicenseResponse> getByProviderId(@PathVariable Long providerId) {
        return licenseService.findByProviderId(providerId).stream()
                .map(LicenseResponse::from)
                .toList();
    }

    @GetMapping("/{licenseId}")
    public LicenseResponse getByIdAndProviderId(@PathVariable Long licenseId, @PathVariable Long providerId) {
        return LicenseResponse.from(licenseService.findByIdAndProviderId(licenseId, providerId));
    }

    @PostMapping
    public ResponseEntity<LicenseResponse> create(@PathVariable Long providerId,
                                                  @Valid @RequestBody CreateLicenseRequest request) {
        License saved = licenseService.addLicense(providerId, request.toEntity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(LicenseResponse.from(saved));
    }
}
