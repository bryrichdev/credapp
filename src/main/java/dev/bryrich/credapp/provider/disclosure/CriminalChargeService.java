package dev.bryrich.credapp.provider.disclosure;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * Disclosed criminal charges. Rows are kept on ON DELETE RESTRICT, so deleting a provider
 * with a disclosure on file fails rather than quietly erasing the history.
 */
@Service
public class CriminalChargeService {

    private final CriminalChargeRepository chargeRepository;
    private final ProviderRepository providerRepository;

    public CriminalChargeService(CriminalChargeRepository chargeRepository,
                                 ProviderRepository providerRepository) {
        this.chargeRepository = chargeRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<CriminalCharge> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return chargeRepository.findByProviderIdOrderByIncidentDateDesc(providerId);
    }

    @Transactional(readOnly = true)
    public List<CriminalCharge> findByProviderIdAndStatus(Long providerId, ChargeStatus status) {
        return chargeRepository.findByProviderIdAndStatus(providerId, status);
    }

    @Transactional(readOnly = true)
    public CriminalCharge findByIdAndProviderId(Long id, Long providerId) {
        return chargeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CriminalChargeNotFoundException(id, providerId));
    }

    @Transactional
    public CriminalCharge addCharge(Long providerId, CriminalCharge charge) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        charge.setProvider(provider);
        return chargeRepository.save(charge);
    }

    @Transactional
    public CriminalCharge update(Long id, Long providerId, Consumer<CriminalCharge> changes) {
        CriminalCharge charge = chargeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CriminalChargeNotFoundException(id, providerId));
        changes.accept(charge);
        return charge;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        CriminalCharge charge = chargeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new CriminalChargeNotFoundException(id, providerId));
        chargeRepository.delete(charge);
    }
}
