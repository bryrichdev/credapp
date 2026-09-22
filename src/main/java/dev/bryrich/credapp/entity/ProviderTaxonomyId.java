package dev.bryrich.credapp.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProviderTaxonomyId implements Serializable {
    private Long providerId;
    private String code;

    protected ProviderTaxonomyId() {}

    public ProviderTaxonomyId(Long providerId, String code) {
        this.providerId = providerId;
        this.code = code;
    }

    public Long getProviderId() {
        return providerId;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderTaxonomyId that)) return false;
        return Objects.equals(providerId, that.providerId)
                && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, code);
    }
}
