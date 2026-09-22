package dev.bryrich.credapp.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GroupTaxonomyId implements Serializable {
    private Long groupId;
    private String code;

    protected GroupTaxonomyId() {}

    public GroupTaxonomyId(Long groupId, String code) {
        this.groupId = groupId;
        this.code = code;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupTaxonomyId that)) return false;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, code);
    }
}
