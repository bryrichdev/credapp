package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.License;

import dev.bryrich.credapp.entity.enums.LicenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    List<License> findByExpirationDateBefore(LocalDate date);

    List<License> findByProviderId(Long providerId);

    Optional<License> findByIdAndProviderId(Long id, Long providerId);

    @Query("SELECT l FROM License l JOIN FETCH l.provider WHERE l.status = :status AND l.expirationDate BETWEEN :from AND :to ORDER BY l.expirationDate")
    List<License> findExpiringWithProvider(@Param("status") LicenseStatus status,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
    @Query(value = """
        SELECT l FROM License l
        JOIN FETCH l.provider p
        WHERE :name = ''
           OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :name, '%'))
           OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :name, '%'))
        """,
            countQuery = """
        SELECT COUNT(l) FROM License l
        JOIN l.provider p
        WHERE :name = ''
           OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :name, '%'))
           OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :name, '%'))
        """)
    Page<License> searchByProviderName(@Param("name") String name, Pageable pageable);

}
