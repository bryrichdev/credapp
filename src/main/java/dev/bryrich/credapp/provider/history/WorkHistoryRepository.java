package dev.bryrich.credapp.provider.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkHistoryRepository extends JpaRepository<WorkHistoryEntry, Long> {
    List<WorkHistoryEntry> findByProviderIdOrderByStartDateDesc(Long providerId);

    Optional<WorkHistoryEntry> findByIdAndProviderId(Long id, Long providerId);
}
