package dev.bryrich.credapp.provider.disclosure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CriminalChargeRepository extends JpaRepository<CriminalCharge, Long> {

    List<CriminalCharge> findByProviderIdOrderByIncidentDateDesc(Long providerId);

    List<CriminalCharge> findByProviderIdAndStatus(Long providerId, ChargeStatus status);

    Optional<CriminalCharge> findByIdAndProviderId(Long id, Long providerId);

    long countByProviderId(Long providerId);
}
