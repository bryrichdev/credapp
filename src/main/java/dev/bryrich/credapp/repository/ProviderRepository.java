package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByNpi(String npi);
    Page<Provider> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);
    @Query("SELECT p FROM Provider p LEFT JOIN FETCH p.licenses WHERE p.id = :id")
    Optional<Provider> findByIdWithLicenses(@Param("id") Long id);

    /**
     * Whether an SSN is stored, without decrypting it. Loading the entity would run
     * SsnConverter and produce a plaintext read that no audit row accounts for.
     */
    @Query("SELECT CASE WHEN p.ssn IS NOT NULL THEN true ELSE false END FROM Provider p WHERE p.id = :id")
    boolean hasSsn(@Param("id") Long id);
}
