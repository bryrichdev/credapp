package dev.bryrich.credapp.provider.history;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.usergroup.GroupScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** A job, or a stretch of time away from work and why. */
@Entity
@Table(name = "provider_work_history")
public class WorkHistoryEntry extends GroupScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private WorkEntryType entryType;

    private String employer;
    private String position;
    private String city;
    private String state;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    private String reasonForLeaving;
    private String gapExplanation;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected WorkHistoryEntry() {
    }

    public WorkHistoryEntry(WorkEntryType entryType, LocalDate startDate) {
        this.entryType = entryType;
        this.startDate = startDate;
    }

    public Long getId() { return id; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public WorkEntryType getEntryType() { return entryType; }
    public void setEntryType(WorkEntryType entryType) { this.entryType = entryType; }
    public boolean isGap() { return entryType == WorkEntryType.GAP; }
    public String getEmployer() { return employer; }
    public void setEmployer(String employer) { this.employer = employer; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getReasonForLeaving() { return reasonForLeaving; }
    public void setReasonForLeaving(String reasonForLeaving) { this.reasonForLeaving = reasonForLeaving; }
    public String getGapExplanation() { return gapExplanation; }
    public void setGapExplanation(String gapExplanation) { this.gapExplanation = gapExplanation; }
}
