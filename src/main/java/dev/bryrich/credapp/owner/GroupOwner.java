package dev.bryrich.credapp.owner;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.group.Group;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "group_owners")
public class GroupOwner extends GroupScopedEntity {

    @EmbeddedId
    private GroupOwnerId id = new GroupOwnerId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("ownerId")
    @JoinColumn(name = "owner_id")
    private Owner owner;

    /** NUMERIC(5,2); the database enforces 0 &lt; percent_owned &lt;= 100. */
    private BigDecimal percentOwned;

    private LocalDate effectiveDate;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected GroupOwner() {}

    public GroupOwner(Group group, Owner owner, BigDecimal percentOwned, LocalDate effectiveDate) {
        this.group = group;
        this.owner = owner;
        this.percentOwned = percentOwned;
        this.effectiveDate = effectiveDate;
    }

    public GroupOwnerId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Owner getOwner() {
        return owner;
    }

    public BigDecimal getPercentOwned() {
        return percentOwned;
    }

    public void setPercentOwned(BigDecimal percentOwned) {
        this.percentOwned = percentOwned;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
