package dev.bryrich.credapp.owner;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GroupOwnerId implements Serializable {

    private Long groupId;
    private Long ownerId;

    protected GroupOwnerId() {}

    public GroupOwnerId(Long groupId, Long ownerId) {
        this.groupId = groupId;
        this.ownerId = ownerId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupOwnerId that)) return false;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(ownerId, that.ownerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, ownerId);
    }
}
