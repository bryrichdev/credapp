package dev.bryrich.credapp.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One NUCC taxonomy code and the specialty it names. The table is reference data, seeded
 * from the published code set rather than entered by a user.
 */
@Entity
@Table(name = "taxonomies")
public class Taxonomy {

    @Id
    private String code;

    @Column(nullable = false)
    private String specialty;

    private String grouping;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Taxonomy() {}

    public Taxonomy(String code, String specialty, String grouping) {
        this.code = code;
        this.specialty = specialty;
        this.grouping = grouping;
    }

    public String getCode() {
        return code;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getGrouping() {
        return grouping;
    }

    public void setGrouping(String grouping) {
        this.grouping = grouping;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** "207Q00000X — Family Medicine", for dropdowns. */
    public String getLabel() {
        return code + " — " + specialty;
    }
}
