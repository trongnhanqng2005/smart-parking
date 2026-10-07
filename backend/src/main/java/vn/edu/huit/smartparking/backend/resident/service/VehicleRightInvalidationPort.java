package vn.edu.huit.smartparking.backend.resident.service;

import java.time.LocalDateTime;
import java.util.List;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.security.entity.User;

public interface VehicleRightInvalidationPort {
    void invalidateForMembershipLosses(List<MembershipAuthorityLoss> losses, User actor);

    void scheduleForMembershipLosses(List<MembershipAuthorityLoss> losses, User actor);

    void voidDependentVehicleRightsForMembership(MembershipVoidSource membership, User actor);

    ResidentStatusResources discoverResidentStatusResources(
            Long residentId,
            ResidentStatus status,
            LocalDateTime statusAt,
            List<VehicleRightAction> vehicleActions,
            List<StatusMembershipAction> membershipActions);

    void applyResidentStatusChange(
            Long residentId,
            ResidentStatus previousStatus,
            ResidentStatus targetStatus,
            LocalDateTime statusAt,
            String reason,
            List<MembershipAuthorityLoss> membershipLosses,
            List<VehicleRightAction> vehicleActions,
            User actor,
            ResidentStatusResources lockedResources);

    record MembershipAuthorityLoss(Long apartmentId, Long residentId, LocalDateTime effectiveAt, String reason) {}

    record MembershipVoidSource(
            Long membershipId,
            Long apartmentId,
            Long residentId,
            MembershipRole memberRole,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            LocalDateTime commandTime,
            String reason) {}

    record VehicleRightAction(
            Long relationId, RelationLifecycleAction action, LocalDateTime effectiveAt, String reason) {}

    record StatusMembershipAction(
            Long membershipId, RelationLifecycleAction action, LocalDateTime effectiveAt, String reason) {}
}
