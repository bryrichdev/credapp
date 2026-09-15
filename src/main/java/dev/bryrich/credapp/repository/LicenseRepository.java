package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.License;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LicenseRepository extends JpaRepository<License, Long> {
    List<License> findByExpirationDateBefore(LocalDate date);
    List<License> findByProviderId(Long providerId);
    List<License> findByStatusAndExpirationDateBetween(String status, LocalDate from, LocalDate to);
}
