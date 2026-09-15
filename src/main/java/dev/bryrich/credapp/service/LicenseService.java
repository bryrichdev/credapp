package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.repository.LicenseRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class LicenseService {
    private final LicenseRepository licenseRepository;
    private final ProviderRepository providerRepository;

    public LicenseService(LicenseRepository licenseRepository,
                          ProviderRepository providerRepository) {
        this.licenseRepository = licenseRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<License> findByProviderId(Long providerId) {
        return licenseRepository.findByProviderId(providerId);
    }

    @Transactional
    public License addLicense(Long providerId, License license) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        provider.addLicense(license);
        return licenseRepository.save(license);
    }

    @Transactional(readOnly = true)
    public List<License> findExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        return licenseRepository.findByStatusAndExpirationDateBetween(
                "active", today, today.plusDays(days));
    }

}
