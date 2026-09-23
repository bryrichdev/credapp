package dev.bryrich.credapp.group.membership;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "group_providers")
public class GroupProvider {

    @EmbeddedId
    private GroupProviderId id = new GroupProviderId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("providerId")
    @JoinColumn(name = "provider_id")
    private Provider provider;

    private LocalDate effectiveDate;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected GroupProvider() {}

    public GroupProvider(Group group, Provider provider, LocalDate effectiveDate) {
        this.group = group;
        this.provider = provider;
        this.effectiveDate = effectiveDate;
    }

    public GroupProviderId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Provider getProvider() {
        return provider;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
