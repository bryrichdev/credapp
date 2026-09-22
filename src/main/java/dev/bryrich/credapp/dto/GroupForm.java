package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class GroupForm {

    @NotBlank(message = "Legal business name is required")
    private String lbn;

    private String dba;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "NPI must be exactly 10 digits")
    private String npi;

    @NotBlank(message = "Tax ID is required")
    @Pattern(regexp = "^[0-9]{9}$", message = "Tax ID must be exactly 9 digits")
    private String taxId;


    /** Empty form, for the create screen. */
    public GroupForm() {
    }

    /** Copies a saved group's values in, so the edit screen renders them. */
    public static GroupForm from(Group group) {
        GroupForm form = new GroupForm();
        form.lbn = group.getLbn();
        form.dba = group.getDba();
        form.npi = group.getNpi();
        form.taxId = group.getTaxId();
        return form;
    }

    public Group toEntity() {
        Group group = new Group(lbn, taxId);
        applyTo(group);
        return group;
    }

    /** Copies this form's values onto an existing group, for updates. */
    public void applyTo(Group group) {
        group.setLbn(lbn);
        group.setDba(blankToNull(dba));
        group.setNpi(blankToNull(npi));
        group.setTaxId(taxId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getLbn() {
        return lbn;
    }

    public void setLbn(String lbn) {
        this.lbn = lbn;
    }

    public String getDba() {
        return dba;
    }

    public void setDba(String dba) {
        this.dba = dba;
    }

    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }
}
