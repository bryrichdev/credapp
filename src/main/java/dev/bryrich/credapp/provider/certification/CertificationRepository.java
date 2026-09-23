package dev.bryrich.credapp.provider.certification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findByProviderIdOrderByEffectiveDateDesc(Long providerId);

    Optional<Certification> findByIdAndProviderId(Long id, Long providerId);

    /**
     * Certifications lapsing in a window, matching the licenses sweep. Lifetime
     * certifications have a null expiration date and are left out by the BETWEEN.
     */
    @Query("""
        SELECT c FROM Certification c
        JOIN FETCH c.provider
        WHERE c.expirationDate BETWEEN :from AND :to
        ORDER BY c.expirationDate
        """)
    List<Certification> findExpiringBetween(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    long countByProviderId(Long providerId);
}
