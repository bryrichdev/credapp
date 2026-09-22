package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.ProviderReference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderReferenceRepository extends JpaRepository<ProviderReference, Long> {

    List<ProviderReference> findByProviderIdOrderByName(Long providerId);

    Optional<ProviderReference> findByIdAndProviderId(Long id, Long providerId);

    long countByProviderId(Long providerId);
}
