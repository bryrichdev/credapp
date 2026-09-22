package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.HospitalPrivilege;
import dev.bryrich.credapp.entity.enums.PrivilegeStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HospitalPrivilegeRepository extends JpaRepository<HospitalPrivilege, Long> {

    /** One provider's privileges, with the admitting colleague loaded where named. */
    @Query("""
        SELECT hp FROM HospitalPrivilege hp
        LEFT JOIN FETCH hp.admittingPhysician
        WHERE hp.provider.id = :providerId
        ORDER BY hp.name
        """)
    List<HospitalPrivilege> findByProviderIdWithAdmittingPhysician(
            @Param("providerId") Long providerId);

    List<HospitalPrivilege> findByProviderId(Long providerId);

    List<HospitalPrivilege> findByProviderIdAndStatus(Long providerId, PrivilegeStatus status);

    /** Rows where this provider is the one admitting on someone else's behalf. */
    List<HospitalPrivilege> findByAdmittingPhysicianId(Long providerId);

    Optional<HospitalPrivilege> findByIdAndProviderId(Long id, Long providerId);
}
