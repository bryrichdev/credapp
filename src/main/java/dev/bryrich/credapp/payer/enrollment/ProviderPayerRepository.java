package dev.bryrich.credapp.payer.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProviderPayerRepository extends JpaRepository<ProviderPayer, Long> {

    @Query("SELECT e FROM ProviderPayer e JOIN FETCH e.payer WHERE e.provider.id = :providerId ORDER BY LOWER(e.payer.name)")
    List<ProviderPayer> findByProviderId(@Param("providerId") Long providerId);

    @Query("SELECT e FROM ProviderPayer e JOIN FETCH e.provider p WHERE e.payer.id = :payerId ORDER BY LOWER(p.lastName), LOWER(p.firstName)")
    List<ProviderPayer> findByPayerId(@Param("payerId") Long payerId);
}
