package dev.bryrich.credapp.provider.location;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A provider working out of one of a group's locations.
 *
 * groupId is carried alongside the two keys because the database enforces two composite
 * foreign keys through it: the location must belong to that group, and the provider must
 * be attached to that same group. It is derived from the location rather than passed in,
 * so it cannot drift. Changing a provider's group memberships can therefore delete rows
 * here by cascade.
 */
@Entity
@Table(name = "provider_locations")
public class ProviderLocation extends GroupScopedEntity {

    @EmbeddedId
    private ProviderLocationId id = new ProviderLocationId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("locationId")
    @JoinColumn(name = "location_id")
    private GroupLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("providerId")
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private PcpScp pcpScp;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ProviderLocation() {}

    public ProviderLocation(GroupLocation location, Provider provider, PcpScp pcpScp) {
        this.location = location;
        this.provider = provider;
        this.groupId = location.getGroup().getId();
        this.pcpScp = pcpScp;
    }

    public ProviderLocationId getId() {
        return id;
    }

    public GroupLocation getLocation() {
        return location;
    }

    public Provider getProvider() {
        return provider;
    }

    public Long getGroupId() {
        return groupId;
    }

    public PcpScp getPcpScp() {
        return pcpScp;
    }

    public void setPcpScp(PcpScp pcpScp) {
        this.pcpScp = pcpScp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
