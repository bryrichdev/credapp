package dev.bryrich.credapp.owner;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "group_owner_relationships")
public class GroupOwnerRelation extends GroupScopedEntity {

    @EmbeddedId
    private GroupOwnerRelationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "group_id", referencedColumnName = "group_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "owner_id", referencedColumnName = "owner_id",
                    insertable = false, updatable = false)
    })
    private GroupOwner owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "group_id", referencedColumnName = "group_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "related_owner_id", referencedColumnName = "owner_id",
                    insertable = false, updatable = false)
    })
    private GroupOwner relatedOwner;

    /** Persisted by RelationshipConverter, which is autoApply. */
    @Column(nullable = false)
    private Relationship relationship;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected GroupOwnerRelation() {}

    /** Reads as "ownerA is {rel} of ownerB". Normalizes id order for gor_ordered. */
    public static GroupOwnerRelation of(Long groupId, Long ownerA, Long ownerB,
                                        Relationship rel) {
        if (ownerA.equals(ownerB)) {
            throw new IllegalArgumentException("Owner cannot be related to themselves");
        }
        GroupOwnerRelation r = new GroupOwnerRelation();
        if (ownerA < ownerB) {
            r.id = new GroupOwnerRelationId(groupId, ownerA, ownerB);
            r.relationship = rel;
        } else {
            r.id = new GroupOwnerRelationId(groupId, ownerB, ownerA);
            r.relationship = rel.inverse();
        }
        return r;
    }

    public GroupOwnerRelationId getId() {
        return id;
    }

    public GroupOwner getOwner() {
        return owner;
    }

    public GroupOwner getRelatedOwner() {
        return relatedOwner;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
