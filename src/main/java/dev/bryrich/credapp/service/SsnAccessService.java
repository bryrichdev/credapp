package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.SsnAccessLog;
import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.entity.enums.SsnSubjectType;
import dev.bryrich.credapp.exception.OwnerNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.exception.SsnAccessDeniedException;
import dev.bryrich.credapp.repository.OwnerRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import dev.bryrich.credapp.repository.SsnAccessLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only route to a decrypted SSN. Every call checks the caller's role and writes an
 * audit row before returning, so there is no way to read one without leaving a trail.
 */
@Service
public class SsnAccessService {

    private final SsnAccessLogRepository logRepository;
    private final OwnerRepository ownerRepository;
    private final ProviderRepository providerRepository;

    public SsnAccessService(SsnAccessLogRepository logRepository,
                            OwnerRepository ownerRepository,
                            ProviderRepository providerRepository) {
        this.logRepository = logRepository;
        this.ownerRepository = ownerRepository;
        this.providerRepository = providerRepository;
    }

    /**
     * Decrypts an owner's SSN and records who asked. Returns null when none is stored,
     * which is still logged — an attempt to look is worth knowing about either way.
     */
    @Transactional
    public String revealOwnerSsn(User actor, Long ownerId, String ipAddress) {
        requireAllowed(actor);
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException(ownerId));

        record(SsnSubjectType.OWNER, ownerId,
                owner.getFirstName() + " " + owner.getLastName(), actor, ipAddress);
        return owner.getSsn();
    }

    /**
     * The provider equivalent. Nothing writes providers.ssn through the web UI yet, so
     * this will report nothing on file until a field for it exists.
     */
    @Transactional
    public String revealProviderSsn(User actor, Long providerId, String ipAddress) {
        requireAllowed(actor);
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        record(SsnSubjectType.PROVIDER, providerId,
                provider.getFirstName() + " " + provider.getLastName(), actor, ipAddress);
        return provider.getSsn();
    }

    /** Whether something is stored, for deciding if the reveal button is worth showing. */
    @Transactional(readOnly = true)
    public boolean ownerSsnOnFile(Long ownerId) {
        return ownerRepository.hasSsn(ownerId);
    }

    @Transactional(readOnly = true)
    public boolean providerSsnOnFile(Long providerId) {
        return providerRepository.hasSsn(providerId);
    }

    @Transactional(readOnly = true)
    public List<SsnAccessLog> recentAccess(SsnSubjectType subjectType, Long subjectId) {
        return logRepository.findTop10BySubjectTypeAndSubjectIdOrderByAccessedAtDesc(
                subjectType, subjectId);
    }

    private void record(SsnSubjectType subjectType, Long subjectId, String subjectName,
                        User actor, String ipAddress) {
        logRepository.save(new SsnAccessLog(subjectType, subjectId, subjectName,
                actor.getId(), actor.getEmail(), ipAddress));
    }

    private void requireAllowed(User actor) {
        if (actor == null || !actor.getRole().canRevealSsn()) {
            throw new SsnAccessDeniedException("You are not allowed to view stored SSNs");
        }
    }
}
