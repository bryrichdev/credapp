package dev.bryrich.credapp.usergroup;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/** Hibernate scopes queries, key lookups, and inserts to the authenticated workspace. */
@MappedSuperclass
public abstract class GroupScopedEntity {
    @TenantId
    @Column(name = "user_group_id", nullable = false, updatable = false)
    private Long userGroupId;

    public Long getUserGroupId() { return userGroupId; }
}
