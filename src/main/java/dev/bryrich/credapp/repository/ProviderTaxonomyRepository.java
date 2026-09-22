package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.ProviderTaxonomy;
import dev.bryrich.credapp.entity.ProviderTaxonomyId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderTaxonomyRepository
        extends JpaRepository<ProviderTaxonomy, ProviderTaxonomyId> {

    /** Every specialty for one provider, primary first, with the code loaded. */
    @Query("""
        SELECT pt FROM ProviderTaxonomy pt
        JOIN FETCH pt.taxonomy t
        WHERE pt.provider.id = :providerId
        ORDER BY pt.primary DESC, t.specialty
        """)
    List<ProviderTaxonomy> findByProviderIdWithTaxonomy(@Param("providerId") Long providerId);

    List<ProviderTaxonomy> findByProviderId(Long providerId);

    Optional<ProviderTaxonomy> findByProviderIdAndPrimaryTrue(Long providerId);

    boolean existsByProviderIdAndTaxonomyCode(Long providerId, String code);

    /**
     * Clears the primary flag for a provider. A partial unique index allows only one
     * primary row, so call this before promoting a different one.
     */
    @Modifying
    @Query("UPDATE ProviderTaxonomy pt SET pt.primary = false WHERE pt.provider.id = :providerId")
    void clearPrimaryForProvider(@Param("providerId") Long providerId);
}
