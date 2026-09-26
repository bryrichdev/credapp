package dev.bryrich.credapp.provider.history;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

public class TrainingForm {

    @NotNull(message = "Choose the kind of training")
    private TrainingType trainingType = TrainingType.RESIDENCY;

    @NotBlank(message = "Institution is required")
    private String institution;

    private String specialty;
    private String city;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "State must be a two-letter code")
    private String state;

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** Checked when the program wasn't finished; an unchecked box sends nothing, so false is the default. */
    private boolean incomplete;

    private String incompleteReason;

    public static TrainingForm from(ProviderTraining entry) {
        TrainingForm form = new TrainingForm();
        form.trainingType = entry.getTrainingType();
        form.institution = entry.getInstitution();
        form.specialty = entry.getSpecialty();
        form.city = entry.getCity();
        form.state = entry.getState();
        form.startDate = entry.getStartDate();
        form.endDate = entry.getEndDate();
        form.incomplete = !entry.isCompleted();
        form.incompleteReason = entry.getIncompleteReason();
        return form;
    }

    public ProviderTraining toEntity() {
        ProviderTraining entry = new ProviderTraining(trainingType, institution.trim(), startDate);
        applyTo(entry);
        return entry;
    }

    public void applyTo(ProviderTraining entry) {
        entry.setTrainingType(trainingType);
        entry.setInstitution(institution.trim());
        entry.setSpecialty(blank(specialty));
        entry.setCity(blank(city));
        entry.setState(state == null || state.isBlank() ? null : state.trim().toUpperCase(Locale.ROOT));
        entry.setStartDate(startDate);
        entry.setEndDate(endDate);
        entry.setCompleted(!incomplete);
        entry.setIncompleteReason(incomplete ? blank(incompleteReason) : null);
    }

    @AssertTrue(message = "End date must be on or after the start date")
    public boolean isEndAfterStart() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Give the reason the training was not completed")
    public boolean isIncompleteExplained() {
        return !incomplete || (incompleteReason != null && !incompleteReason.isBlank());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public TrainingType getTrainingType() { return trainingType; }
    public void setTrainingType(TrainingType trainingType) { this.trainingType = trainingType; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isIncomplete() { return incomplete; }
    public void setIncomplete(boolean incomplete) { this.incomplete = incomplete; }
    public String getIncompleteReason() { return incompleteReason; }
    public void setIncompleteReason(String incompleteReason) { this.incompleteReason = incompleteReason; }
}
