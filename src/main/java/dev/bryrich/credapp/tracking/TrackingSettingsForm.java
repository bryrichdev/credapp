package dev.bryrich.credapp.tracking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The settings page. The limits match the database's checks. */
public class TrackingSettingsForm {

    @NotNull(message = "Enter a number of days")
    @Min(value = 1, message = "At least 1 day")
    @Max(value = 365, message = "At most 365 days")
    private Integer warningDays;

    @NotNull(message = "Enter a number of days")
    @Min(value = 1, message = "At least 1 day")
    @Max(value = 365, message = "At most 365 days")
    private Integer urgentDays;

    @NotNull(message = "Enter a number of days")
    @Min(value = 7, message = "At least 7 days")
    @Max(value = 365, message = "At most 365 days")
    private Integer stalledDays;

    @NotNull(message = "Enter a number of days")
    @Min(value = 30, message = "At least 30 days")
    @Max(value = 365, message = "At most 365 days")
    private Integer caqhDays;

    public static TrackingSettingsForm from(TrackingSettings settings) {
        TrackingSettingsForm form = new TrackingSettingsForm();
        form.warningDays = settings.getWarningDays();
        form.urgentDays = settings.getUrgentDays();
        form.stalledDays = settings.getStalledDays();
        form.caqhDays = settings.getCaqhDays();
        return form;
    }

    public void applyTo(TrackingSettings settings) {
        settings.setWarningDays(warningDays);
        settings.setUrgentDays(urgentDays);
        settings.setStalledDays(stalledDays);
        settings.setCaqhDays(caqhDays);
    }

    /** "Due soon" is the nearer part of "coming up", so it can't reach further out. */
    @AssertTrue(message = "Due soon has to fit within coming up")
    public boolean isUrgentWithinWarning() {
        return urgentDays == null || warningDays == null || urgentDays <= warningDays;
    }

    public Integer getWarningDays() {
        return warningDays;
    }

    public void setWarningDays(Integer warningDays) {
        this.warningDays = warningDays;
    }

    public Integer getUrgentDays() {
        return urgentDays;
    }

    public void setUrgentDays(Integer urgentDays) {
        this.urgentDays = urgentDays;
    }

    public Integer getStalledDays() {
        return stalledDays;
    }

    public void setStalledDays(Integer stalledDays) {
        this.stalledDays = stalledDays;
    }

    public Integer getCaqhDays() {
        return caqhDays;
    }

    public void setCaqhDays(Integer caqhDays) {
        this.caqhDays = caqhDays;
    }
}
