package dev.bryrich.credapp.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Legal business name. */
    @Column(nullable = false)
    private String lbn;

    /** Doing-business-as name; null when the group trades under its legal name. */
    private String dba;

    private String npi;

    @Column(nullable = false)
    private String taxId;

    private String specialty;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupLocation> locations = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupOwner> owners = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupProvider> providers = new ArrayList<>();

    protected Group() {}

    public Group(String lbn, String taxId) {
        this.lbn = lbn;
        this.taxId = taxId;
    }

    public Long getId() {
        return id;
    }

    public String getLbn() {
        return lbn;
    }

    public void setLbn(String lbn) {
        this.lbn = lbn;
    }

    public String getDba() {
        return dba;
    }

    public void setDba(String dba) {
        this.dba = dba;
    }

    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<GroupLocation> getLocations() {
        return locations;
    }

    public void addLocation(GroupLocation location) {
        locations.add(location);
        location.setGroup(this);
    }

    public void removeLocation(GroupLocation location) {
        locations.remove(location);
        location.setGroup(null);
    }

    public List<GroupOwner> getOwners() {
        return owners;
    }

    public List<GroupProvider> getProviders() {
        return providers;
    }
}
