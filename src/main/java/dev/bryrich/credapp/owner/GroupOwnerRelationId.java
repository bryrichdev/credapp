package dev.bryrich.credapp.owner;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * The column names here are explicit on purpose. This entity's two GroupOwner
 * associations map the same physical columns read-only, and without @Column Hibernate
 * derives the logical names from the attributes (groupId, ownerId) instead. It then sees
 * two logical names for one physical column and refuses to build the EntityManagerFactory.
 */
@Embeddable
public class GroupOwnerRelationId implements Serializable {

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "related_owner_id")
    private Long relatedOwnerId;

    protected GroupOwnerRelationId() {}

    public GroupOwnerRelationId(Long groupId, Long ownerId, Long relatedOwnerId) {
        this.groupId = groupId;
        this.ownerId = ownerId;
        this.relatedOwnerId = relatedOwnerId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getRelatedOwnerId() {
        return relatedOwnerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupOwnerRelationId that)) return false;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(relatedOwnerId, that.relatedOwnerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, ownerId, relatedOwnerId);
    }
}
