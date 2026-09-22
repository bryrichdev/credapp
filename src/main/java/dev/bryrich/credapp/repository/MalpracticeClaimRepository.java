package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.MalpracticeClaim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MalpracticeClaimRepository extends JpaRepository<MalpracticeClaim, Long> {

    List<MalpracticeClaim> findByProviderId(Long providerId);

    List<MalpracticeClaim> findByPolicyId(Long policyId);

    Optional<MalpracticeClaim> findByCarrierNameAndClaimNumber(String carrierName,
                                                               String claimNumber);

    Optional<MalpracticeClaim> findByIdAndProviderId(Long id, Long providerId);

    long countByProviderId(Long providerId);
}
