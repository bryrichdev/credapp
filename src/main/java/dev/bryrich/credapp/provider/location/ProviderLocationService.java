package dev.bryrich.credapp.provider.location;

import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.group.location.GroupLocationNotFoundException;
import dev.bryrich.credapp.group.location.GroupLocationRepository;
import dev.bryrich.credapp.group.membership.GroupProviderRepository;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Which locations a provider works out of. The location's group and the provider's group
 * memberships have to agree; the database enforces that through composite foreign keys, and
 * this checks first so the coordinator gets a message rather than a constraint violation.
 */
@Service
public class ProviderLocationService {

    private final ProviderLocationRepository providerLocationRepository;
    private final GroupLocationRepository groupLocationRepository;
    private final GroupProviderRepository groupProviderRepository;
    private final ProviderRepository providerRepository;

    public ProviderLocationService(ProviderLocationRepository providerLocationRepository,
                                   GroupLocationRepository groupLocationRepository,
                                   GroupProviderRepository groupProviderRepository,
                                   ProviderRepository providerRepository) {
        this.providerLocationRepository = providerLocationRepository;
        this.groupLocationRepository = groupLocationRepository;
        this.groupProviderRepository = groupProviderRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<ProviderLocation> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return providerLocationRepository.findByProviderIdWithLocation(providerId);
    }

    @Transactional(readOnly = true)
    public List<ProviderLocation> findByLocationId(Long locationId) {
        return providerLocationRepository.findByLocationIdWithProvider(locationId);
    }

    @Transactional(readOnly = true)
    public ProviderLocation findOne(Long locationId, Long providerId) {
        return providerLocationRepository.findByLocationIdAndProviderId(locationId, providerId)
                .orElseThrow(() -> new ProviderLocationNotFoundException(locationId, providerId));
    }

    /** Locations a provider could be placed at: those belonging to groups they are in. */
    @Transactional(readOnly = true)
    public List<GroupLocation> findAssignableLocations(Long providerId) {
        return groupProviderRepository.findByProviderId(providerId).stream()
                .map(membership -> membership.getGroup().getId())
                .flatMap(groupId -> groupLocationRepository.findByGroupId(groupId).stream())
                .toList();
    }

    @Transactional
    public ProviderLocation assign(Long locationId, Long providerId, PcpScp pcpScp) {
        GroupLocation location = groupLocationRepository.findById(locationId)
                .orElseThrow(() -> new GroupLocationNotFoundException(locationId));
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        Long groupId = location.getGroup().getId();
        if (!groupProviderRepository.existsByGroupIdAndProviderId(groupId, providerId)) {
            throw new ProviderNotInGroupException(providerId, groupId);
        }

        return providerLocationRepository.findByLocationIdAndProviderId(locationId, providerId)
                .map(existing -> {
                    existing.setPcpScp(pcpScp);
                    return existing;
                })
                .orElseGet(() -> providerLocationRepository.save(
                        new ProviderLocation(location, provider, pcpScp)));
    }

    @Transactional
    public void unassign(Long locationId, Long providerId) {
        providerLocationRepository.findByLocationIdAndProviderId(locationId, providerId)
                .ifPresent(providerLocationRepository::delete);
    }
}
