package dev.bryrich.credapp.provider.reference;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class ProviderReferenceService {

    private final ProviderReferenceRepository referenceRepository;
    private final ProviderRepository providerRepository;

    public ProviderReferenceService(ProviderReferenceRepository referenceRepository,
                                    ProviderRepository providerRepository) {
        this.referenceRepository = referenceRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<ProviderReference> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return referenceRepository.findByProviderIdOrderByName(providerId);
    }

    @Transactional(readOnly = true)
    public ProviderReference findByIdAndProviderId(Long id, Long providerId) {
        return referenceRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ProviderReferenceNotFoundException(id, providerId));
    }

    @Transactional
    public ProviderReference addReference(Long providerId, ProviderReference reference) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        reference.setProvider(provider);
        return referenceRepository.save(reference);
    }

    @Transactional
    public ProviderReference update(Long id, Long providerId, Consumer<ProviderReference> changes) {
        ProviderReference reference = referenceRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ProviderReferenceNotFoundException(id, providerId));
        changes.accept(reference);
        return reference;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        ProviderReference reference = referenceRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ProviderReferenceNotFoundException(id, providerId));
        referenceRepository.delete(reference);
    }
}
