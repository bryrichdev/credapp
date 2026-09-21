package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Payer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayerRepository extends JpaRepository<Payer, Long> {

    Optional<Payer> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Payer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = "contacts")
    Optional<Payer> findWithContactsById(Long id);
}
