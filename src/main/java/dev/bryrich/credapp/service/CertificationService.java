package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Certification;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.exception.CertificationNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.CertificationRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

@Service
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final ProviderRepository providerRepository;

    public CertificationService(CertificationRepository certificationRepository,
                                ProviderRepository providerRepository) {
        this.certificationRepository = certificationRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<Certification> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return certificationRepository.findByProviderIdOrderByEffectiveDateDesc(providerId);
    }

    @Transactional(readOnly = true)
    public Certification findByIdAndProviderId(Long id, Long providerId) {
        return certificationRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CertificationNotFoundException(id, providerId));
    }

    /**
     * Certifications lapsing within the window, matching the licenses sweep. Lifetime
     * certifications have no expiration date and never appear here.
     */
    @Transactional(readOnly = true)
    public List<Certification> findExpiringSoon(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1, was " + days);
        }
        LocalDate today = LocalDate.now();
        return certificationRepository.findExpiringBetween(today, today.plusDays(days));
    }

    @Transactional
    public Certification addCertification(Long providerId, Certification certification) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        certification.setProvider(provider);
        return certificationRepository.save(certification);
    }

    @Transactional
    public Certification update(Long id, Long providerId, Consumer<Certification> changes) {
        Certification certification = certificationRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CertificationNotFoundException(id, providerId));
        changes.accept(certification);
        return certification;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        Certification certification = certificationRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CertificationNotFoundException(id, providerId));
        certificationRepository.delete(certification);
    }
}
