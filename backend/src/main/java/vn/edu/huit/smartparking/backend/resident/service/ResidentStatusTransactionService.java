package vn.edu.huit.smartparking.backend.resident.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentMembershipRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.service.GuarantorChainConflictException;

@Service
public class ResidentStatusTransactionService {
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final ApartmentMembershipRepository membershipRepository;
    private final VehicleRightInvalidationPort vehicleRightInvalidationPort;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ResidentStatusTransactionService(
            ResidentRepository residentRepository,
            ApartmentRepository apartmentRepository,
            ApartmentMembershipRepository membershipRepository,
            VehicleRightInvalidationPort vehicleRightInvalidationPort,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.membershipRepository = membershipRepository;
        this.vehicleRightInvalidationPort = vehicleRightInvalidationPort;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public ResidentDetail changeStatus(
            Long id,
            ResidentStatusChangeRequest request,
            User actor,
            LocalDateTime commandTime,
            ResidentStatusResources expectedResources,
            List<VehicleRightInvalidationPort.VehicleRightAction> vehicleActions,
            List<VehicleRightInvalidationPort.StatusMembershipAction> statusMembershipActions) {
        Map<Long, Resident> lockedResidents = new LinkedHashMap<>();
        for (Long residentId : expectedResources.residentIds()) {
            Resident resident = residentRepository.findByIdForUpdate(residentId)
                    .orElseThrow(ResidentNotFoundException::new);
            lockedResidents.put(residentId, resident);
        }
        Resident resident = lockedResidents.get(id);
        if (resident == null) {
            throw new ResidentNotFoundException();
        }
        validateTransition(resident.getStatus(), request.status());

        List<ApartmentMembership> discoveredMemberships = membershipRepository.findActiveForResident(
                id, MembershipStatus.ACTIVE);
        Map<Long, ApartmentMembership> membershipById = discoveredMemberships.stream()
                .collect(Collectors.toMap(ApartmentMembership::getId, Function.identity()));
        Map<Long, ResidentStatusChangeRequest.MembershipAction> requestedMembershipActions =
                membershipActions(request, membershipById, id, commandTime);
        List<Long> missingApartmentIds = discoveredMemberships.stream()
                .map(membership -> membership.getApartment().getId())
                .filter(apartmentId -> !expectedResources.apartmentIds().contains(apartmentId))
                .distinct().toList();
        if (!missingApartmentIds.isEmpty()) {
            throw new ResidentStatusLockSetChangedException(
                    new ResidentStatusResources(List.of(), missingApartmentIds, List.of()));
        }

        if (request.status() == ResidentStatus.INACTIVE) {
            Set<Long> effectiveMembershipIds = discoveredMemberships.stream()
                    .filter(membership -> effectiveAt(
                            membership.getValidFrom(), membership.getValidTo(), commandTime))
                    .map(ApartmentMembership::getId).collect(Collectors.toSet());
            if (!requestedMembershipActions.keySet().containsAll(effectiveMembershipIds)) {
                throw new ResidentStatusConflictException();
            }
        }

        for (Long apartmentId : expectedResources.apartmentIds()) {
            apartmentRepository.findByIdForUpdate(apartmentId).orElseThrow(ResidentStatusResourceNotFoundException::new);
        }

        ResidentStatusResources currentResources = vehicleRightInvalidationPort.discoverResidentStatusResources(
                id, request.status(), commandTime, vehicleActions, statusMembershipActions);
        if (!expectedResources.contains(currentResources)) {
            throw new ResidentStatusLockSetChangedException(currentResources);
        }
        List<VehicleRightInvalidationPort.MembershipAuthorityLoss> membershipLosses =
                requestedMembershipActions.values().stream()
                        .map(action -> {
                            ApartmentMembership membership = membershipById.get(action.membershipId());
                            return new VehicleRightInvalidationPort.MembershipAuthorityLoss(
                                    membership.getApartment().getId(), id, action.effectiveAt(), action.reason());
                        })
                        .toList();
        vehicleRightInvalidationPort.applyResidentStatusChange(
                id, resident.getStatus(), request.status(), commandTime, request.reason(), membershipLosses,
                vehicleActions, actor, expectedResources);

        List<ApartmentMembership> lockedMemberships = membershipRepository.findActiveForResidentForUpdate(
                id, MembershipStatus.ACTIVE);
        if (!sameMembershipSet(discoveredMemberships, lockedMemberships)) {
            throw new ResidentConcurrentModificationException();
        }
        Map<Long, ApartmentMembership> lockedMembershipById = lockedMemberships.stream()
                .collect(Collectors.toMap(ApartmentMembership::getId, Function.identity()));
        for (ResidentStatusChangeRequest.MembershipAction action : requestedMembershipActions.values()) {
            ApartmentMembership membership = lockedMembershipById.get(action.membershipId());
            if (membership == null) {
                throw new ResidentStatusRelationStateConflictException();
            }
            validateMembershipAction(membership, action, commandTime);
            String oldData = membershipAuditData(membership, membership.getStatus(),
                    membership.getValidTo(), membership.getLifecycleChangedAt(), membership.getLifecycleReason());
            MembershipStatus nextStatus = action.action() == RelationLifecycleAction.END
                    ? MembershipStatus.INACTIVE : MembershipStatus.REVOKED;
            membership.setStatus(nextStatus);
            membership.setValidTo(action.effectiveAt());
            membership.setLifecycleChangedAt(commandTime);
            membership.setLifecycleReason(action.reason());
            ApartmentMembership saved = membershipRepository.saveAndFlush(membership);
            String actionName = action.action() == RelationLifecycleAction.END
                    ? "MEMBERSHIP_ENDED" : "MEMBERSHIP_REVOKED";
            auditService.record(actionName, "APARTMENT_MEMBERSHIP", saved.getId().toString(), actor,
                    oldData, membershipAuditData(saved, nextStatus, action.effectiveAt(), commandTime, action.reason()));
        }

        ResidentStatus previousStatus = resident.getStatus();
        String oldData = residentStatusAuditData(previousStatus, null, List.of(), List.of());
        resident.setStatus(request.status());
        resident.setUpdatedAt(commandTime);
        Resident savedResident = residentRepository.saveAndFlush(resident);
        List<Long> membershipActionIds = new java.util.ArrayList<>(requestedMembershipActions.keySet());
        List<Long> vehicleActionIds = vehicleActions.stream()
                .map(VehicleRightInvalidationPort.VehicleRightAction::relationId).toList();
        auditService.record("RESIDENT_STATUS_CHANGED", "RESIDENT", id.toString(), actor, oldData,
                residentStatusAuditData(savedResident.getStatus(), request.reason(), membershipActionIds, vehicleActionIds));
        return detail(savedResident);
    }

    private Map<Long, ResidentStatusChangeRequest.MembershipAction> membershipActions(
            ResidentStatusChangeRequest request,
            Map<Long, ApartmentMembership> activeMemberships,
            Long residentId,
            LocalDateTime commandTime) {
        Map<Long, ResidentStatusChangeRequest.MembershipAction> requested = new LinkedHashMap<>();
        if (request.membershipActions() == null) {
            return requested;
        }
        for (ResidentStatusChangeRequest.MembershipAction action : request.membershipActions()) {
            ApartmentMembership membership = activeMemberships.get(action.membershipId());
            if (membership == null) {
                ApartmentMembership existing = membershipRepository.findById(action.membershipId())
                        .orElseThrow(ResidentStatusResourceNotFoundException::new);
                if (!residentId.equals(existing.getResident().getId())) {
                    throw new ResidentStatusResourceNotFoundException();
                }
                throw new ResidentStatusRelationStateConflictException();
            }
            validateMembershipAction(membership, action, commandTime);
            requested.put(action.membershipId(), action);
        }
        return requested;
    }

    private void validateMembershipAction(
            ApartmentMembership membership,
            ResidentStatusChangeRequest.MembershipAction action,
            LocalDateTime commandTime) {
        if (membership.getStatus() != MembershipStatus.ACTIVE
                || !action.effectiveAt().isAfter(membership.getValidFrom())
                || action.effectiveAt().isAfter(commandTime)
                || membership.getValidTo() != null && action.effectiveAt().isAfter(membership.getValidTo())) {
            throw new ResidentStatusRelationStateConflictException();
        }
    }

    private void validateTransition(ResidentStatus current, ResidentStatus target) {
        boolean allowed = switch (current) {
            case ACTIVE -> target == ResidentStatus.INACTIVE || target == ResidentStatus.BLOCKED;
            case INACTIVE -> target == ResidentStatus.ACTIVE;
            case BLOCKED -> target == ResidentStatus.ACTIVE || target == ResidentStatus.INACTIVE;
        };
        if (!allowed) {
            throw new ResidentStatusConflictException();
        }
    }

    private boolean effectiveAt(LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }

    private boolean sameMembershipSet(List<ApartmentMembership> discovered, List<ApartmentMembership> locked) {
        if (discovered.size() != locked.size()) {
            return false;
        }
        Map<Long, ApartmentMembership> lockedById = locked.stream()
                .collect(Collectors.toMap(ApartmentMembership::getId, Function.identity()));
        return discovered.stream().allMatch(membership -> {
            ApartmentMembership current = lockedById.get(membership.getId());
            return current != null && Objects.equals(membership.getValidFrom(), current.getValidFrom())
                    && Objects.equals(membership.getValidTo(), current.getValidTo())
                    && membership.getMemberRole() == current.getMemberRole()
                    && membership.getStatus() == current.getStatus();
        });
    }

    private String membershipAuditData(
            ApartmentMembership membership,
            MembershipStatus status,
            LocalDateTime validTo,
            LocalDateTime lifecycleChangedAt,
            String reason) {
        return serialize(new MembershipStatusAuditData(membership.getApartment().getId(), membership.getResident().getId(),
                membership.getMemberRole(), membership.getValidFrom(), validTo, status, lifecycleChangedAt, reason));
    }

    private String residentStatusAuditData(
            ResidentStatus status, String reason, List<Long> membershipActionIds, List<Long> vehicleActionIds) {
        return serialize(new ResidentStatusAuditData(status, reason, membershipActionIds, vehicleActionIds));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize Resident status audit data", exception);
        }
    }

    private ResidentDetail detail(Resident resident) {
        return new ResidentDetail(resident.getId(), resident.getFullName(), resident.getIdentityNumber(),
                resident.getDateOfBirth(), resident.getPhone(), resident.getEmail(), resident.getStatus(),
                resident.getCreatedAt(), resident.getUpdatedAt());
    }

    private record MembershipStatusAuditData(
            @JsonProperty("apartment_id") Long apartmentId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("member_role") vn.edu.huit.smartparking.backend.resident.enums.MembershipRole memberRole,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            @JsonProperty("status") MembershipStatus status,
            @JsonProperty("lifecycle_changed_at") LocalDateTime lifecycleChangedAt,
            @JsonProperty("reason") String reason) {}

    private record ResidentStatusAuditData(
            @JsonProperty("status") ResidentStatus status,
            @JsonProperty("reason") String reason,
            @JsonProperty("membership_action_ids") List<Long> membershipActionIds,
            @JsonProperty("vehicle_right_action_ids") List<Long> vehicleActionIds) {}
}
