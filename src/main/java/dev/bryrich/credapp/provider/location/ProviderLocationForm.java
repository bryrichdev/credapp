package dev.bryrich.credapp.provider.location;

import jakarta.validation.constraints.NotNull;

/**
 * Places a provider at one of a group's locations. The location has to belong to a group
 * the provider is already assigned to; the service checks that before saving.
 */
public class ProviderLocationForm {

    @NotNull(message = "Location is required")
    private Long locationId;

    @NotNull(message = "PCP or SCP is required")
    private PcpScp pcpScp;

    /** Empty form, for the create screen. */
    public ProviderLocationForm() {
    }

    /** Copies a saved assignment's values in, so the edit screen renders them. */
    public static ProviderLocationForm from(ProviderLocation assignment) {
        ProviderLocationForm form = new ProviderLocationForm();
        form.locationId = assignment.getLocation().getId();
        form.pcpScp = assignment.getPcpScp();
        return form;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public PcpScp getPcpScp() {
        return pcpScp;
    }

    public void setPcpScp(PcpScp pcpScp) {
        this.pcpScp = pcpScp;
    }
}
