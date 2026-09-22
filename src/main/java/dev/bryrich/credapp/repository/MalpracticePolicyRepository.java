package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.MalpracticePolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MalpracticePolicyRepository extends JpaRepository<MalpracticePolicy, Long> {

    List<MalpracticePolicy> findByProviderIdOrderByEffectiveDateDesc(Long providerId);

    List<MalpracticePolicy> findByGroupIdOrderByEffectiveDateDesc(Long groupId);

    Optional<MalpracticePolicy> findByCarrierNameAndPolicyNumber(String carrierName,
                                                                 String policyNumber);

    /** Policies lapsing in a window, for the renewal sweep. Lifetime policies are excluded. */
    @Query("""
        SELECT mp FROM MalpracticePolicy mp
        LEFT JOIN FETCH mp.provider
        LEFT JOIN FETCH mp.group
        WHERE mp.expirationDate BETWEEN :from AND :to
        ORDER BY mp.expirationDate
        """)
    List<MalpracticePolicy> findExpiringBetween(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
