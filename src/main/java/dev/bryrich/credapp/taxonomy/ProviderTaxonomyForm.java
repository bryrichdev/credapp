package dev.bryrich.credapp.taxonomy;

import jakarta.validation.constraints.NotBlank;

/** Picks a taxonomy code for a provider. The code itself comes from the seeded table. */
public class ProviderTaxonomyForm {

    @NotBlank(message = "Specialty is required")
    private String code;

    private boolean primary;

    /** Empty form, for the create screen. */
    public ProviderTaxonomyForm() {
    }

    /** Copies a saved row's values in, so the edit screen renders them. */
    public static ProviderTaxonomyForm from(ProviderTaxonomy taxonomy) {
        ProviderTaxonomyForm form = new ProviderTaxonomyForm();
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
