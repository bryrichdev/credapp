package dev.bryrich.credapp.payer;

import dev.bryrich.credapp.usergroup.GroupScopedEntity;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A contact at a payer. Its scope is payer-wide, group-level, or provider-level, never a
 * mix: the database enforces that with {@code payer_contacts_scope_exclusive}, and the
 * factory methods below are the intended way to build one.
 */
@Entity
@Table(name = "payer_contacts")
public class PayerContact extends GroupScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    /** Free text, e.g. "provider rep", "credentialing analyst". */
    @Column(nullable = false)
    private String role;

    private String phoneNumber;
    private String faxNumber;
    private String emailAddress;
    private String address;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected PayerContact() {}

    private PayerContact(Payer payer, String role) {
        this.payer = payer;
        this.role = role;
    }

    /** A contact for the payer as a whole. */
    public static PayerContact forPayer(Payer payer, String role) {
        return new PayerContact(payer, role);
    }

    /** A contact for one group's dealings with this payer. */
    public static PayerContact forGroup(Payer payer, Group group, String role) {
        PayerContact c = new PayerContact(payer, role);
        c.group = group;
        return c;
    }

    /** A contact for one provider's dealings with this payer. */
    public static PayerContact forProvider(Payer payer, Provider provider, String role) {
        PayerContact c = new PayerContact(payer, role);
        c.provider = provider;
        return c;
    }

    public Long getId() {
        return id;
    }

    public Payer getPayer() {
        return payer;
    }

    public void setPayer(Payer payer) {
        this.payer = payer;
    }

    public Group getGroup() {
        return group;
    }

    /** Setting a group clears the provider, so the scope stays exclusive. */
    public void setGroup(Group group) {
        this.group = group;
        if (group != null) {
            this.provider = null;
        }
    }

    public Provider getProvider() {
        return provider;
    }

    /** Setting a provider clears the group, so the scope stays exclusive. */
    public void setProvider(Provider provider) {
        this.provider = provider;
        if (provider != null) {
            this.group = null;
        }
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFaxNumber() {
        return faxNumber;
    }

    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
