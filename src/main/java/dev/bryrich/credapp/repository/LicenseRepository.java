package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.License;

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
    List<License> findExpiringWithProvider(@Param("status") String status,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

}
