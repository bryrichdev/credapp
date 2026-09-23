package dev.bryrich.credapp.group.membership;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GroupProviderId implements Serializable {
    private Long groupId;
    private Long providerId;

    protected GroupProviderId() {}

    public GroupProviderId(Long groupId, Long providerId) {
        this.groupId = groupId;
        this.providerId = providerId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getProviderId() {
        return providerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupProviderId that)) return false;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(providerId, that.providerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, providerId);
    }
}
