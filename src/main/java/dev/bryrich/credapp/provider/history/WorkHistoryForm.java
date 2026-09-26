package dev.bryrich.credapp.provider.history;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

public class WorkHistoryForm {

    @NotNull(message = "Choose a job or time away")
    private WorkEntryType entryType = WorkEntryType.JOB;

    private String employer;
    private String position;
    private String city;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String reasonForLeaving;
    private String gapExplanation;

    public static WorkHistoryForm from(WorkHistoryEntry entry) {
        WorkHistoryForm form = new WorkHistoryForm();
        form.entryType = entry.getEntryType();
        form.employer = entry.getEmployer();
        form.position = entry.getPosition();
        form.city = entry.getCity();
        form.state = entry.getState();
        form.startDate = entry.getStartDate();
        form.endDate = entry.getEndDate();
        form.reasonForLeaving = entry.getReasonForLeaving();
        form.gapExplanation = entry.getGapExplanation();
        return form;
    }

    public WorkHistoryEntry toEntity() {
        WorkHistoryEntry entry = new WorkHistoryEntry(entryType, startDate);
        applyTo(entry);
        return entry;
    }

    public void applyTo(WorkHistoryEntry entry) {
        boolean gap = entryType == WorkEntryType.GAP;
        entry.setEntryType(entryType);
        entry.setEmployer(gap ? null : blank(employer));
        entry.setPosition(gap ? null : blank(position));
        entry.setCity(gap ? null : blank(city));
        entry.setState(gap || state == null || state.isBlank() ? null : state.trim().toUpperCase(Locale.ROOT));
        entry.setStartDate(startDate);
        entry.setEndDate(endDate);
        entry.setReasonForLeaving(gap ? null : blank(reasonForLeaving));
        entry.setGapExplanation(gap ? blank(gapExplanation) : null);
    }

    @AssertTrue(message = "Employer is required for a job")
    public boolean isEmployerGiven() {
        return entryType != WorkEntryType.JOB || (employer != null && !employer.isBlank());
    }

    @AssertTrue(message = "Explain the time away")
    public boolean isGapExplained() {
        return entryType != WorkEntryType.GAP || (gapExplanation != null && !gapExplanation.isBlank());
    }

    @AssertTrue(message = "Time away needs an end date")
    public boolean isGapEnded() {
        return entryType != WorkEntryType.GAP || endDate != null;
    }

    @AssertTrue(message = "End date must be on or after the start date")
    public boolean isEndAfterStart() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public WorkEntryType getEntryType() { return entryType; }
    public void setEntryType(WorkEntryType entryType) { this.entryType = entryType; }
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
