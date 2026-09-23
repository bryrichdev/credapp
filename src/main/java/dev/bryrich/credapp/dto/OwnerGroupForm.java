package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupOwner;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gives an owner a stake in a group from the owner's own page. From the group's side,
 * stakes are owner rows on the group form (GroupProfileForm.OwnerRow).
 */
public class OwnerGroupForm {

    @NotNull(message = "Group is required")
    private Long groupId;

    @DecimalMin(value = "0.01", message = "Percent owned must be greater than 0")
    @DecimalMax(value = "100.00", message = "Percent owned cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Percent owned allows at most two decimal places")
    private BigDecimal percentOwned;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    public OwnerGroupForm() {
    }

    public static OwnerGroupForm from(GroupOwner groupOwner) {
        OwnerGroupForm form = new OwnerGroupForm();
        form.groupId = groupOwner.getGroup().getId();
        form.percentOwned = groupOwner.getPercentOwned();
        form.effectiveDate = groupOwner.getEffectiveDate();
        return form;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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
