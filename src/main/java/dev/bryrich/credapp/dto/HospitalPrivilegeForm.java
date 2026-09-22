package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.HospitalPrivilege;
import dev.bryrich.credapp.entity.enums.PrivilegeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HospitalPrivilegeForm {

    @NotBlank(message = "Hospital name is required")
    private String name;

    @NotNull(message = "Status is required")
    private PrivilegeStatus status = PrivilegeStatus.ACTIVE;

    /** Optional colleague who admits on this provider's behalf. Cannot be the provider. */
    private Long admittingPhysicianId;

    /** Empty form, for the create screen. */
    public HospitalPrivilegeForm() {
    }

    /** Copies a saved privilege's values in, so the edit screen renders them. */
    public static HospitalPrivilegeForm from(HospitalPrivilege privilege) {
        HospitalPrivilegeForm form = new HospitalPrivilegeForm();
        form.name = privilege.getName();
        form.status = privilege.getStatus();
        form.admittingPhysicianId = privilege.getAdmittingPhysician() == null
                ? null : privilege.getAdmittingPhysician().getId();
        return form;
    }

    public HospitalPrivilege toEntity() {
        return new HospitalPrivilege(name, status);
    }

    /** Copies this form's values onto an existing privilege. The colleague is set by the service. */
    public void applyTo(HospitalPrivilege privilege) {
        privilege.setName(name);
        privilege.setStatus(status);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PrivilegeStatus getStatus() {
        return status;
    }

    public void setStatus(PrivilegeStatus status) {
        this.status = status;
    }

    public Long getAdmittingPhysicianId() {
        return admittingPhysicianId;
    }

    public void setAdmittingPhysicianId(Long admittingPhysicianId) {
        this.admittingPhysicianId = admittingPhysicianId;
    }
}
