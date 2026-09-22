package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.HospitalPrivilege;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.exception.HospitalPrivilegeNotFoundException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.HospitalPrivilegeRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * Admitting privileges. A provider without their own names a colleague who admits for
 * them; a CHECK constraint stops that being the provider themselves, and this rejects it
 * first so the message is readable.
 */
@Service
public class HospitalPrivilegeService {

    private final HospitalPrivilegeRepository privilegeRepository;
    private final ProviderRepository providerRepository;

    public HospitalPrivilegeService(HospitalPrivilegeRepository privilegeRepository,
                                    ProviderRepository providerRepository) {
        this.privilegeRepository = privilegeRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<HospitalPrivilege> findByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException(providerId);
        }
        return privilegeRepository.findByProviderIdWithAdmittingPhysician(providerId);
    }

    @Transactional(readOnly = true)
    public HospitalPrivilege findByIdAndProviderId(Long id, Long providerId) {
        return privilegeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new HospitalPrivilegeNotFoundException(id, providerId));
    }

    /** Rows where this provider admits on someone else's behalf. */
    @Transactional(readOnly = true)
    public List<HospitalPrivilege> findAdmittingFor(Long providerId) {
        return privilegeRepository.findByAdmittingPhysicianId(providerId);
    }

    @Transactional
    public HospitalPrivilege addPrivilege(Long providerId, HospitalPrivilege privilege,
                                          Long admittingPhysicianId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        privilege.setProvider(provider);
        privilege.setAdmittingPhysician(resolveAdmittingPhysician(providerId, admittingPhysicianId));
        return privilegeRepository.save(privilege);
    }

    @Transactional
    public HospitalPrivilege update(Long id, Long providerId, Long admittingPhysicianId,
                                    Consumer<HospitalPrivilege> changes) {
        HospitalPrivilege privilege = privilegeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new HospitalPrivilegeNotFoundException(id, providerId));
        changes.accept(privilege);
        privilege.setAdmittingPhysician(resolveAdmittingPhysician(providerId, admittingPhysicianId));
        return privilege;
    }

    @Transactional
    public void delete(Long id, Long providerId) {
        HospitalPrivilege privilege = privilegeRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new HospitalPrivilegeNotFoundException(id, providerId));
        privilegeRepository.delete(privilege);
    }

    private Provider resolveAdmittingPhysician(Long providerId, Long admittingPhysicianId) {
        if (admittingPhysicianId == null) {
            return null;
        }
        if (admittingPhysicianId.equals(providerId)) {
            throw new IllegalArgumentException(
                    "A provider cannot be listed as their own admitting physician");
        }
        return providerRepository.findById(admittingPhysicianId)
                .orElseThrow(() -> new ProviderNotFoundException(admittingPhysicianId));
    }
}
