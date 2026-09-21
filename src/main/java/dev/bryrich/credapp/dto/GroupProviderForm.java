package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupProvider;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Assigns an existing provider to a group. */
public class GroupProviderForm {

    @NotNull(message = "Provider is required")
    private Long providerId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    public GroupProviderForm() {
    }

    public static GroupProviderForm from(GroupProvider groupProvider) {
        GroupProviderForm form = new GroupProviderForm();
        form.providerId = groupProvider.getProvider().getId();
        form.effectiveDate = groupProvider.getEffectiveDate();
        return form;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
