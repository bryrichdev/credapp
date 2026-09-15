package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByNpi(String npi);
    List<Provider> findByLastNameContainingIgnoreCase(String lastName);
    @Query("SELECT p FROM Provider p LEFT JOIN FETCH p.licenses WHERE p.id = :id")
    Optional<Provider> findByIdWithLicenses(@Param("id") Long id);
}
