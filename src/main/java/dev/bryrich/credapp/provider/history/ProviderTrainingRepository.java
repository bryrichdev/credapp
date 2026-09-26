package dev.bryrich.credapp.provider.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderTrainingRepository extends JpaRepository<ProviderTraining, Long> {
    List<ProviderTraining> findByProviderIdOrderByStartDateDesc(Long providerId);

    Optional<ProviderTraining> findByIdAndProviderId(Long id, Long providerId);
}
