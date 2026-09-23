package dev.bryrich.credapp.group.membership;

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

/** Which providers are assigned to which groups. A provider can be in several. */
@Service
public class GroupProviderService {

    private final GroupProviderRepository groupProviderRepository;
    private final GroupRepository groupRepository;
    private final ProviderRepository providerRepository;

    public GroupProviderService(GroupProviderRepository groupProviderRepository,
                                GroupRepository groupRepository,
                                ProviderRepository providerRepository) {
        this.groupProviderRepository = groupProviderRepository;
        this.groupRepository = groupRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupProvider> findProviders(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        return groupProviderRepository.findByGroupIdWithProvider(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupProvider> findGroups(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return groupProviderRepository.findByProviderIdWithGroup(providerId);
    }

    @Transactional(readOnly = true)
    public boolean isAssigned(Long groupId, Long providerId) {
        return groupProviderRepository.existsByGroupIdAndProviderId(groupId, providerId);
    }

    @Transactional
    public GroupProvider assign(Long groupId, Long providerId, LocalDate effectiveDate) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        return groupProviderRepository.findByGroupIdAndProviderId(groupId, providerId)
                .map(existing -> {
                    existing.setEffectiveDate(effectiveDate);
                    return existing;
                })
                .orElseGet(() -> groupProviderRepository.save(
                        new GroupProvider(group, provider, effectiveDate)));
    }

    @Transactional
    public void unassign(Long groupId, Long providerId) {
        groupProviderRepository.findByGroupIdAndProviderId(groupId, providerId)
                .ifPresent(groupProviderRepository::delete);
    }
}
