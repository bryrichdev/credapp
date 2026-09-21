package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupOwnerRelation;
import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.entity.enums.Relationship;

/**
 * Reads as "ownerName is {relationship} of relatedOwnerName", which is the direction the
 * row is stored in. Build it from a query that fetched both owners.
 */
public record OwnerRelationResponse(
        Long groupId,
        Long ownerId,
        String ownerName,
        Long relatedOwnerId,
        String relatedOwnerName,
        Relationship relationship
) {
    public static OwnerRelationResponse from(GroupOwnerRelation r) {
        Owner a = r.getOwner().getOwner();
        Owner b = r.getRelatedOwner().getOwner();
        return new OwnerRelationResponse(
                r.getId().getGroupId(),
                a.getId(), a.getFirstName() + " " + a.getLastName(),
                b.getId(), b.getFirstName() + " " + b.getLastName(),
                r.getRelationship());
    }

    /** The same row phrased from the other person's side. */
    public OwnerRelationResponse flipped() {
        return new OwnerRelationResponse(groupId, relatedOwnerId, relatedOwnerName,
                ownerId, ownerName, relationship.inverse());
    }
}
