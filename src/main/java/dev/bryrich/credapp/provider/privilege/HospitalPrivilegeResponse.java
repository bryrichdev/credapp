package dev.bryrich.credapp.provider.privilege;

import java.time.Instant;

public record HospitalPrivilegeResponse(
        Long id,
        Long providerId,
        String name,
        PrivilegeStatus status,
        Long admittingPhysicianId,
        Instant createdAt,
        Instant updatedAt
) {
    public static HospitalPrivilegeResponse from(HospitalPrivilege h) {
        return new HospitalPrivilegeResponse(h.getId(), h.getProvider().getId(), h.getName(),
                h.getStatus(),
                h.getAdmittingPhysician() == null ? null : h.getAdmittingPhysician().getId(),
                h.getCreatedAt(), h.getUpdatedAt());
    }
}
