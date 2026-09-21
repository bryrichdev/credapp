package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupOwner;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Attaches an existing owner to a group, with their stake in it. */
public class GroupOwnerForm {

    @NotNull(message = "Owner is required")
    private Long ownerId;

    @DecimalMin(value = "0.01", message = "Percent owned must be greater than 0")
    @DecimalMax(value = "100.00", message = "Percent owned cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Percent owned allows at most two decimal places")
    private BigDecimal percentOwned;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    public GroupOwnerForm() {
    }

    public static GroupOwnerForm from(GroupOwner groupOwner) {
        GroupOwnerForm form = new GroupOwnerForm();
        form.ownerId = groupOwner.getOwner().getId();
        form.percentOwned = groupOwner.getPercentOwned();
        form.effectiveDate = groupOwner.getEffectiveDate();
        return form;
    }

    /** Copies this form's values onto an existing link, for updates. */
    public void applyTo(GroupOwner groupOwner) {
        groupOwner.setPercentOwned(percentOwned);
        groupOwner.setEffectiveDate(effectiveDate);
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public BigDecimal getPercentOwned() {
        return percentOwned;
    }

    public void setPercentOwned(BigDecimal percentOwned) {
        this.percentOwned = percentOwned;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
