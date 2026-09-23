package dev.bryrich.credapp.malpractice;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupNotFoundException;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/** Malpractice coverage. A policy belongs to one provider or one group, never both. */
@Service
public class MalpracticePolicyService {

    private final MalpracticePolicyRepository policyRepository;
    private final ProviderRepository providerRepository;
    private final GroupRepository groupRepository;

    public MalpracticePolicyService(MalpracticePolicyRepository policyRepository,
                                    ProviderRepository providerRepository,
                                    GroupRepository groupRepository) {
        this.policyRepository = policyRepository;
        this.providerRepository = providerRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional(readOnly = true)
    public List<MalpracticePolicy> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return policyRepository.findByProviderIdOrderByEffectiveDateDesc(providerId);
    }

    @Transactional(readOnly = true)
    public List<MalpracticePolicy> findByGroupId(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        return policyRepository.findByGroupIdOrderByEffectiveDateDesc(groupId);
    }

    @Transactional(readOnly = true)
    public MalpracticePolicy findById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new MalpracticePolicyNotFoundException(id));
    }

    /** Policies lapsing within the window, for the renewal sweep. */
    @Transactional(readOnly = true)
    public List<MalpracticePolicy> findExpiringSoon(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1, was " + days);
        }
        LocalDate today = LocalDate.now();
        return policyRepository.findExpiringBetween(today, today.plusDays(days));
    }

    @Transactional
    public MalpracticePolicy addForProvider(Long providerId, String policyNumber,
                                            String carrierName, String typeOfCoverage,
                                            LocalDate effectiveDate, CoverageScope scope) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        return policyRepository.save(MalpracticePolicy.forProvider(provider, policyNumber,
                carrierName, typeOfCoverage, effectiveDate, scope));
    }

    @Transactional
    public MalpracticePolicy addForGroup(Long groupId, String policyNumber,
                                         String carrierName, String typeOfCoverage,
                                         LocalDate effectiveDate, CoverageScope scope) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        return policyRepository.save(MalpracticePolicy.forGroup(group, policyNumber,
                carrierName, typeOfCoverage, effectiveDate, scope));
    }

    /** Rejects a request that names both owners or neither, before the CHECK constraint does. */
    public void requireExactlyOneOwner(Long providerId, Long groupId) {
        if ((providerId == null) == (groupId == null)) {
            throw new PolicyOwnerException();
        }
    }

    @Transactional
    public MalpracticePolicy update(Long id, Consumer<MalpracticePolicy> changes) {
        MalpracticePolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new MalpracticePolicyNotFoundException(id));
        changes.accept(policy);
        return policy;
    }

    @Transactional
    public void delete(Long id) {
        MalpracticePolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new MalpracticePolicyNotFoundException(id));
        policyRepository.delete(policy);
    }
}
