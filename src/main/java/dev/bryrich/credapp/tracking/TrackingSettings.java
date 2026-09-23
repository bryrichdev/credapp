package dev.bryrich.credapp.tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * How far ahead one user group wants to hear about what's coming due. Keyed by the group
 * itself rather than scoped to it: there's one row per group, looked up by its id.
 */
@Entity
@Table(name = "tracking_settings")
public class TrackingSettings {

    public static final int DEFAULT_WARNING_DAYS = 90;
    public static final int DEFAULT_URGENT_DAYS = 30;
    public static final int DEFAULT_STALLED_DAYS = 60;
    public static final int DEFAULT_CAQH_DAYS = 120;

    @Id
    @Column(name = "user_group_id")
    private Long userGroupId;

    private int warningDays = DEFAULT_WARNING_DAYS;
    private int urgentDays = DEFAULT_URGENT_DAYS;
    private int stalledDays = DEFAULT_STALLED_DAYS;
    private int caqhDays = DEFAULT_CAQH_DAYS;

    @UpdateTimestamp
    private Instant updatedAt;

    protected TrackingSettings() {
    }

    /** The defaults, for a group that hasn't saved its own. */
    public TrackingSettings(Long userGroupId) {
        this.userGroupId = userGroupId;
    }

    public Long getUserGroupId() {
        return userGroupId;
    }

    public int getWarningDays() {
        return warningDays;
    }

    public void setWarningDays(int warningDays) {
        this.warningDays = warningDays;
    }

    public int getUrgentDays() {
        return urgentDays;
    }

    public void setUrgentDays(int urgentDays) {
        this.urgentDays = urgentDays;
    }

    public int getStalledDays() {
        return stalledDays;
    }

    public void setStalledDays(int stalledDays) {
        this.stalledDays = stalledDays;
    }

    public int getCaqhDays() {
        return caqhDays;
    }

    public void setCaqhDays(int caqhDays) {
        this.caqhDays = caqhDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
