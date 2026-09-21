package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.enums.Relationship;
import jakarta.validation.constraints.NotNull;

/**
 * Reads as "ownerId is {relationship} of relatedOwnerId". The service normalizes the id
 * order and flips the relationship when it has to, so the form can take them either way.
 */
public class OwnerRelationForm {

    @NotNull(message = "Owner is required")
    private Long ownerId;

    @NotNull(message = "Related owner is required")
    private Long relatedOwnerId;

    @NotNull(message = "Relationship is required")
    private Relationship relationship;

    public OwnerRelationForm() {
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getRelatedOwnerId() {
        return relatedOwnerId;
    }

    public void setRelatedOwnerId(Long relatedOwnerId) {
        this.relatedOwnerId = relatedOwnerId;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }
}
