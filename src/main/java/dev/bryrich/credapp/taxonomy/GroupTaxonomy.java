package dev.bryrich.credapp.taxonomy;

import dev.bryrich.credapp.group.Group;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A group's specialty, replacing the free-text groups.specialty column dropped in V5. */
@Entity
@Table(name = "group_taxonomies")
public class GroupTaxonomy {

    @EmbeddedId
    private GroupTaxonomyId id = new GroupTaxonomyId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private Group group;

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

    protected GroupTaxonomy() {}

    public GroupTaxonomy(Group group, Taxonomy taxonomy, boolean isPrimary) {
        this.group = group;
        this.taxonomy = taxonomy;
        this.primary = isPrimary;
    }

    public GroupTaxonomyId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
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
