package dev.bryrich.credapp.taxonomy;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A specialty a provider practises under. At most one row per provider may be primary;
 * a partial unique index enforces that, so check before flipping the flag.
 */
@Entity
@Table(name = "provider_taxonomies")
public class ProviderTaxonomy extends GroupScopedEntity {

    @EmbeddedId
    private ProviderTaxonomyId id = new ProviderTaxonomyId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("providerId")
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("code")
    @JoinColumn(name = "code")
    private Taxonomy taxonomy;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ProviderTaxonomy() {}

    public ProviderTaxonomy(Provider provider, Taxonomy taxonomy, boolean isPrimary) {
        this.provider = provider;
        this.taxonomy = taxonomy;
        this.primary = isPrimary;
    }

    public ProviderTaxonomyId getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public Taxonomy getTaxonomy() {
        return taxonomy;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
