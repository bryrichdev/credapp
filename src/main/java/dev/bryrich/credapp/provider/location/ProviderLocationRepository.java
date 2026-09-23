package dev.bryrich.credapp.provider.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderLocationRepository
        extends JpaRepository<ProviderLocation, ProviderLocationId> {

    /** Every location one provider works out of, with the location and its group loaded. */
    @Query("""
        SELECT pl FROM ProviderLocation pl
        JOIN FETCH pl.location l
        JOIN FETCH l.group
        WHERE pl.provider.id = :providerId
        ORDER BY l.locationName
        """)
    List<ProviderLocation> findByProviderIdWithLocation(@Param("providerId") Long providerId);

    /** Every provider working out of one location, with the provider loaded. */
    @Query("""
        SELECT pl FROM ProviderLocation pl
        JOIN FETCH pl.provider p
        WHERE pl.location.id = :locationId
        ORDER BY p.lastName, p.firstName
        """)
    List<ProviderLocation> findByLocationIdWithProvider(@Param("locationId") Long locationId);

    List<ProviderLocation> findByProviderId(Long providerId);

    List<ProviderLocation> findByLocationId(Long locationId);

    List<ProviderLocation> findByGroupId(Long groupId);

    Optional<ProviderLocation> findByLocationIdAndProviderId(Long locationId, Long providerId);

    boolean existsByLocationIdAndProviderId(Long locationId, Long providerId);

    long countByLocationId(Long locationId);
}
