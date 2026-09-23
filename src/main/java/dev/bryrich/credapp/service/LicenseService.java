package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.License;
import dev.bryrich.credapp.entity.enums.LicenseStatus;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.exception.LicenseNotFoundException;
import dev.bryrich.credapp.repository.LicenseRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
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
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
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
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1, was " + days);
        }
        LocalDate today = LocalDate.now();
        return licenseRepository.findExpiringWithProvider(LicenseStatus.ACTIVE, today, today.plusDays(days));
    }

    /** With the provider loaded, for the Licenses tab's edit screen. */
    @Transactional(readOnly = true)
    public License findById(Long id) {
        License license = licenseRepository.findById(id)
                .orElseThrow(() -> new LicenseNotFoundException(id));
        license.getProvider().getFirstName();
        return license;
    }

    @Transactional(readOnly = true)
    public License findByIdAndProviderId(Long id, Long providerId) {
        return licenseRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new LicenseNotFoundException(id, providerId));
    }

    @Transactional(readOnly = true)
    public Page<License> findAll(Pageable pageable) {
        return licenseRepository.findAll(pageable);
    }

    @Transactional
    public License update(Long id, Long providerId, java.util.function.Consumer<License> changes) {
        License license = licenseRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new LicenseNotFoundException(id, providerId));
        changes.accept(license);
        return license;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        License license = licenseRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new LicenseNotFoundException(id, providerId));
        licenseRepository.delete(license);
    }

    @Transactional(readOnly = true)
    public Page<License> searchByProviderName(@Param("providerName") String providerName, Pageable pageable) {
        return licenseRepository.searchByProviderName(providerName, pageable);
    }

}
