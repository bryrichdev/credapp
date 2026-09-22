package dev.bryrich.credapp.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProviderLocationId implements Serializable {
    private Long locationId;
    private Long providerId;

    protected ProviderLocationId() {}

    public ProviderLocationId(Long locationId, Long providerId) {
        this.locationId = locationId;
        this.providerId = providerId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public Long getProviderId() {
        return providerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderLocationId that)) return false;
        return Objects.equals(locationId, that.locationId)
                && Objects.equals(providerId, that.providerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, providerId);
    }
}
