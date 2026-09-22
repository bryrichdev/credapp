package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.GroupTaxonomy;
import jakarta.validation.constraints.NotBlank;

/** Picks a taxonomy code for a group, replacing the free-text specialty dropped in V5. */
public class GroupTaxonomyForm {

    @NotBlank(message = "Specialty is required")
    private String code;

    private boolean primary;

    /** Empty form, for the create screen. */
    public GroupTaxonomyForm() {
    }

    /** Copies a saved row's values in, so the edit screen renders them. */
    public static GroupTaxonomyForm from(GroupTaxonomy taxonomy) {
        GroupTaxonomyForm form = new GroupTaxonomyForm();
        form.code = taxonomy.getTaxonomy().getCode();
        form.primary = taxonomy.isPrimary();
        return form;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
