package dev.bryrich.credapp.provider.privilege;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HospitalPrivilegeForm {

    @NotBlank(message = "Hospital name is required")
    private String name;

    @NotNull(message = "Status is required")
    private PrivilegeStatus status = PrivilegeStatus.ACTIVE;

    /** Optional colleague who admits on this provider's behalf. Cannot be the provider. */
    private Long admittingPhysicianId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reappointmentDate;

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
        form.reappointmentDate = privilege.getReappointmentDate();
        return form;
    }

    public HospitalPrivilege toEntity() {
        HospitalPrivilege privilege = new HospitalPrivilege(name, status);
        privilege.setReappointmentDate(reappointmentDate);
        return privilege;
    }

    /** Copies this form's values onto an existing privilege. The colleague is set by the service. */
    public void applyTo(HospitalPrivilege privilege) {
        privilege.setName(name);
        privilege.setStatus(status);
        privilege.setReappointmentDate(reappointmentDate);
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

    public LocalDate getReappointmentDate() {
        return reappointmentDate;
    }

    public void setReappointmentDate(LocalDate reappointmentDate) {
        this.reappointmentDate = reappointmentDate;
    }
}
