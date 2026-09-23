package dev.bryrich.credapp.taxonomy;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Which specialties a provider practises under. A partial unique index allows one primary
 * row per provider, so promoting one clears the rest in the same transaction.
 */
@Service
public class ProviderTaxonomyService {

    private final ProviderTaxonomyRepository providerTaxonomyRepository;
    private final ProviderRepository providerRepository;
    private final TaxonomyRepository taxonomyRepository;

    public ProviderTaxonomyService(ProviderTaxonomyRepository providerTaxonomyRepository,
                                   ProviderRepository providerRepository,
                                   TaxonomyRepository taxonomyRepository) {
        this.providerTaxonomyRepository = providerTaxonomyRepository;
        this.providerRepository = providerRepository;
        this.taxonomyRepository = taxonomyRepository;
    }

    @Transactional(readOnly = true)
    public List<ProviderTaxonomy> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return providerTaxonomyRepository.findByProviderIdWithTaxonomy(providerId);
    }

    @Transactional(readOnly = true)
    public ProviderTaxonomy findPrimary(Long providerId) {
        return providerTaxonomyRepository.findByProviderIdAndPrimaryTrue(providerId)
                .orElse(null);
    }

    @Transactional
    public ProviderTaxonomy assign(Long providerId, String code, boolean primary) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        Taxonomy taxonomy = taxonomyRepository.findById(code)
                .orElseThrow(() -> new TaxonomyNotFoundException(code));

        if (primary) {
            providerTaxonomyRepository.clearPrimaryForProvider(providerId);
        }

        return providerTaxonomyRepository.findByProviderId(providerId).stream()
                .filter(existing -> existing.getTaxonomy().getCode().equals(code))
                .findFirst()
                .map(existing -> {
                    existing.setPrimary(primary);
                    return existing;
                })
                .orElseGet(() -> providerTaxonomyRepository.save(
                        new ProviderTaxonomy(provider, taxonomy, primary)));
    }

    @Transactional
    public void unassign(Long providerId, String code) {
        ProviderTaxonomy assignment = providerTaxonomyRepository
                .findById(new ProviderTaxonomyId(providerId, code))
                .orElseThrow(() -> new ProviderTaxonomyNotFoundException(providerId, code));
        providerTaxonomyRepository.delete(assignment);
    }
}
