package dev.bryrich.credapp.entity;

import dev.bryrich.credapp.entity.enums.PrivilegeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Admitting privileges a provider holds at a hospital. A provider without privileges of
 * their own names a colleague who admits on their behalf; that colleague cannot be the
 * provider themselves, which a CHECK constraint enforces.
 */
@Entity
@Table(name = "hospital_privileges")
public class HospitalPrivilege {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private PrivilegeStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admitting_physician")
    private Provider admittingPhysician;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected HospitalPrivilege() {}

    public HospitalPrivilege(String name, PrivilegeStatus status) {
        this.name = name;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PrivilegeStatus getStatus() {
        return status;
    }

    public void setStatus(PrivilegeStatus status) {
        this.status = status;
    }

    public Provider getAdmittingPhysician() {
        return admittingPhysician;
    }

    public void setAdmittingPhysician(Provider admittingPhysician) {
        this.admittingPhysician = admittingPhysician;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
