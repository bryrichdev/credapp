package dev.bryrich.credapp.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Assigns a provider to a group from the provider's own page. The mirror image of
 * GroupProviderForm, which does the same thing from the group's page.
 */
public class ProviderGroupForm {

    @NotNull(message = "Group is required")
    private Long groupId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    public ProviderGroupForm() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
