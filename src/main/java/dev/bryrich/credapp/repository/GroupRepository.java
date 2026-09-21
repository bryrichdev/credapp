package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByNpi(String npi);

    boolean existsByNpi(String npi);

    /** tax_id is not unique: several groups can share a TIN. */
    List<Group> findByTaxId(String taxId);

    Page<Group> findByLbnContainingIgnoreCase(String lbn, Pageable pageable);

    @EntityGraph(attributePaths = "locations")
    Optional<Group> findWithLocationsById(Long id);

    @EntityGraph(attributePaths = {"owners", "owners.owner"})
    Optional<Group> findWithOwnersById(Long id);

    @EntityGraph(attributePaths = {"providers", "providers.provider"})
    Optional<Group> findWithProvidersById(Long id);
}
