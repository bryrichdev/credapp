package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.MalpracticeClaim;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.exception.MalpracticeClaimNotFoundException;
import dev.bryrich.credapp.exception.MalpracticePolicyNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.MalpracticeClaimRepository;
import dev.bryrich.credapp.repository.MalpracticePolicyRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class MalpracticeClaimService {

    private final MalpracticeClaimRepository claimRepository;
    private final MalpracticePolicyRepository policyRepository;
    private final ProviderRepository providerRepository;

    public MalpracticeClaimService(MalpracticeClaimRepository claimRepository,
                                   MalpracticePolicyRepository policyRepository,
                                   ProviderRepository providerRepository) {
        this.claimRepository = claimRepository;
        this.policyRepository = policyRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<MalpracticeClaim> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return claimRepository.findByProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public MalpracticeClaim findByIdAndProviderId(Long id, Long providerId) {
        return claimRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new MalpracticeClaimNotFoundException(id, providerId));
    }

    @Transactional
    public MalpracticeClaim addClaim(Long providerId, MalpracticeClaim claim, Long policyId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        claim.setProvider(provider);
        if (policyId != null) {
            MalpracticePolicy policy = policyRepository.findById(policyId)
                    .orElseThrow(() -> new MalpracticePolicyNotFoundException(policyId));
            claim.setPolicy(policy);
        }
        return claimRepository.save(claim);
    }

    @Transactional
    public MalpracticeClaim update(Long id, Long providerId, Consumer<MalpracticeClaim> changes) {
        MalpracticeClaim claim = claimRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new MalpracticeClaimNotFoundException(id, providerId));
        changes.accept(claim);
        return claim;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        MalpracticeClaim claim = claimRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new MalpracticeClaimNotFoundException(id, providerId));
        claimRepository.delete(claim);
    }
}
