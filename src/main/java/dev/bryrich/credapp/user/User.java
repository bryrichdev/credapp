package dev.bryrich.credapp.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    @Column(nullable = false)
    private Role role = Role.COORDINATOR;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "user_group_id", nullable = false)
    private Long userGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus membershipStatus = MembershipStatus.APPROVED;

    public Long getUserGroupId() { return userGroupId; }
    public void setUserGroupId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("A user group is required");
        }
        userGroupId = value;
    }
    public MembershipStatus getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(MembershipStatus value) { membershipStatus = value; }
    public boolean isPendingApproval() { return membershipStatus == MembershipStatus.PENDING; }

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected User() {}

    public User(String email, String passwordHash, Long userGroupId) {
        setEmail(email);
        this.passwordHash = passwordHash;
        setUserGroupId(userGroupId);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email is required").trim().toLowerCase(Locale.ROOT);
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled && membershipStatus == MembershipStatus.APPROVED
                && userGroupId != null && userGroupId > 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
