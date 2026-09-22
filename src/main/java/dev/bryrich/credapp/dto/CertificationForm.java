package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Certification;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class CertificationForm {

    @NotBlank(message = "Board is required")
    private String board;

    @NotNull(message = "Effective date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    /** Left empty for a lifetime certification. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expirationDate;

    /** Empty form, for the create screen. */
    public CertificationForm() {
    }

    /** Copies a saved certification's values in, so the edit screen renders them. */
    public static CertificationForm from(Certification certification) {
        CertificationForm form = new CertificationForm();
        form.board = certification.getBoard();
        form.effectiveDate = certification.getEffectiveDate();
        form.expirationDate = certification.getExpirationDate();
        return form;
    }

    public Certification toEntity() {
        Certification certification = new Certification(board, effectiveDate);
        certification.setExpirationDate(expirationDate);
        return certification;
    }

    /** Copies this form's values onto an existing certification, for updates. */
    public void applyTo(Certification certification) {
        certification.setBoard(board);
        certification.setEffectiveDate(effectiveDate);
        certification.setExpirationDate(expirationDate);
    }

    @AssertTrue(message = "Expiration date must be after the effective date")
    public boolean isExpirationAfterEffective() {
        return expirationDate == null || effectiveDate == null
                || expirationDate.isAfter(effectiveDate);
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }
}
