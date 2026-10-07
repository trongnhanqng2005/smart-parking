package vn.edu.huit.smartparking.backend.vehicle.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightLifecycleRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightVoidRequest;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleResidentRelationRepository;

@Service
public class VehicleRightLifecycleService {
    private static final String RELATION_ENTITY = "VEHICLE_RESIDENT_RELATION";

    private final VehicleResidentRelationRepository relationRepository;
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final VehicleRightInvalidationService invalidationService;

    public VehicleRightLifecycleService(
            VehicleResidentRelationRepository relationRepository,
            ResidentRepository residentRepository,
            ApartmentRepository apartmentRepository,
            VehicleRepository vehicleRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            VehicleRightInvalidationService invalidationService) {
        this.relationRepository = relationRepository;
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.invalidationService = invalidationService;
    }

    @Transactional
    public VehicleRightDetail end(Long id, VehicleRightLifecycleRequest request, User actor) {
        return changeLifecycle(id, request, VehicleRelationStatus.INACTIVE, "VEHICLE_RIGHT_ENDED", actor);
    }

    @Transactional
    public VehicleRightDetail revoke(Long id, VehicleRightLifecycleRequest request, User actor) {
        return changeLifecycle(id, request, VehicleRelationStatus.REVOKED, "VEHICLE_RIGHT_REVOKED", actor);
    }

    @Transactional
    public VehicleRightDetail voidRight(Long id, VehicleRightVoidRequest request, User actor) {
        if (id == null || id <= 0 || request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new VehicleInvalidRequestException();
        }
        VehicleResidentRelation discovered = relationRepository.findById(id)
                .orElseThrow(VehicleOwnerRelationNotFoundException::new);
        TreeSet<Long> residentIds = new TreeSet<>();
        residentIds.add(discovered.getResident().getId());
        if (discovered.getGuarantorResident() != null) {
            residentIds.add(discovered.getGuarantorResident().getId());
        }
        for (Long residentId : residentIds) {
            residentRepository.findByIdForUpdate(residentId).orElseThrow(VehicleNotFoundException::new);
        }

        List<Long> apartmentIds = List.of();
        if (discovered.getRelationType() == VehicleRelationType.OWNER) {
            apartmentIds = invalidationService.lockOwnerVoidApartmentContexts(discovered.getVehicle().getId());
        } else if (discovered.getGuarantorApartment() != null) {
            apartmentRepository.findByIdForUpdate(discovered.getGuarantorApartment().getId())
                    .orElseThrow(VehicleNotFoundException::new);
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(discovered.getVehicle().getId())
                .orElseThrow(VehicleNotFoundException::new);
        List<VehicleResidentRelation> lockedRelations = relationRepository.findAllForVehicleForUpdate(vehicle.getId());
        VehicleResidentRelation relation = lockedRelations.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst().orElseThrow(VehicleRelationStateConflictException::new);
        if (!vehicle.getId().equals(relation.getVehicle().getId())
                || !discovered.getResident().getId().equals(relation.getResident().getId())
                || relation.getRelationType() != discovered.getRelationType()
                || !java.util.Objects.equals(discovered.getValidFrom(), relation.getValidFrom())
                || !java.util.Objects.equals(discovered.getValidTo(), relation.getValidTo())) {
            throw new VehicleConcurrentModificationException();
        }
        if (relation.getStatus() == VehicleRelationStatus.VOID
                || relation.getStatus() == VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED) {
            throw new VehicleRelationStateConflictException();
        }

        LocalDateTime commandTime = LocalDateTime.now();
        if (relation.getRelationType() == VehicleRelationType.OWNER) {
            invalidationService.voidDependentAuthorizedUsersForOwner(
                    relation, lockedRelations, apartmentIds, commandTime, request.reason(), actor);
        }
        String oldData = auditData(relation, relation.getLifecycleReason());
        relation.setStatus(VehicleRelationStatus.VOID);
        relation.setLifecycleChangedAt(commandTime);
        relation.setLifecycleReason(request.reason());
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        auditService.record("VEHICLE_RIGHT_VOIDED", RELATION_ENTITY, saved.getId().toString(), actor, oldData,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    private VehicleRightDetail changeLifecycle(
            Long id, VehicleRightLifecycleRequest request, VehicleRelationStatus status, String action, User actor) {
        if (id == null || id <= 0 || request == null || request.effectiveAt() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new VehicleInvalidRequestException();
        }
        VehicleResidentRelation discovered = relationRepository.findById(id)
                .orElseThrow(VehicleOwnerRelationNotFoundException::new);
        TreeSet<Long> residentIds = new TreeSet<>();
        residentIds.add(discovered.getResident().getId());
        if (discovered.getGuarantorResident() != null) {
            residentIds.add(discovered.getGuarantorResident().getId());
        }
        for (Long residentId : residentIds) {
            residentRepository.findByIdForUpdate(residentId).orElseThrow(VehicleNotFoundException::new);
        }
        LocalDateTime effectiveAt = request.effectiveAt();
        List<Long> apartmentIds = List.of();
        if (discovered.getRelationType() == VehicleRelationType.OWNER) {
            apartmentIds = invalidationService.lockOwnerLossApartmentContexts(
                    discovered.getVehicle().getId(), discovered.getResident().getId(), effectiveAt);
        } else if (discovered.getGuarantorApartment() != null) {
            apartmentRepository.findByIdForUpdate(discovered.getGuarantorApartment().getId())
                    .orElseThrow(VehicleNotFoundException::new);
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(discovered.getVehicle().getId())
                .orElseThrow(VehicleNotFoundException::new);
        List<VehicleResidentRelation> lockedRelations = relationRepository.findAllActiveForVehicleForUpdate(
                vehicle.getId(), VehicleRelationStatus.ACTIVE);
        VehicleResidentRelation relation = lockedRelations.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst().orElseThrow(VehicleRelationStateConflictException::new);
        if (!vehicle.getId().equals(relation.getVehicle().getId())
                || !discovered.getResident().getId().equals(relation.getResident().getId())) {
            throw new VehicleRelationStateConflictException();
        }

        LocalDateTime commandTime = LocalDateTime.now();
        if (!effectiveAt.isAfter(relation.getValidFrom()) || effectiveAt.isAfter(commandTime)
                || relation.getValidTo() != null && effectiveAt.isAfter(relation.getValidTo())) {
            throw new VehicleRelationStateConflictException();
        }

        if (relation.getRelationType() == VehicleRelationType.OWNER) {
            invalidationService.invalidateOwnerLoss(relation.getResident().getId(), effectiveAt, request.reason(),
                    actor, apartmentIds, lockedRelations);
        }

        String oldData = auditData(relation, relation.getLifecycleReason());
        relation.setStatus(status);
        relation.setValidTo(effectiveAt);
        relation.setLifecycleChangedAt(commandTime);
        relation.setLifecycleReason(request.reason());
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        auditService.record(action, RELATION_ENTITY, saved.getId().toString(), actor, oldData,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    private VehicleRightDetail detail(VehicleResidentRelation relation) {
        return new VehicleRightDetail(relation.getId(), relation.getVehicle().getId(), relation.getResident().getId(),
                relation.getRelationType(), relation.getGuarantorType(),
                relation.getGuarantorResident() == null ? null : relation.getGuarantorResident().getId(),
                relation.getGuarantorApartment() == null ? null : relation.getGuarantorApartment().getId(),
                relation.getValidFrom(), relation.getValidTo(), relation.getStatus(),
                relation.getLifecycleChangedAt(), relation.getLifecycleReason(), relation.getCreatedAt());
    }

    private String auditData(VehicleResidentRelation relation, String reason) {
        try {
            return objectMapper.writeValueAsString(new VehicleRightLifecycleAuditData(
                    relation.getVehicle().getId(), relation.getResident().getId(), relation.getRelationType(),
                    relation.getGuarantorType(),
                    relation.getGuarantorResident() == null ? null : relation.getGuarantorResident().getId(),
                    relation.getGuarantorApartment() == null ? null : relation.getGuarantorApartment().getId(),
                    relation.getValidFrom(), relation.getValidTo(), relation.getStatus(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize VehicleRight lifecycle audit data", exception);
        }
    }

    private record VehicleRightLifecycleAuditData(
            @JsonProperty("vehicle_id") Long vehicleId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("relation_type") VehicleRelationType relationType,
            @JsonProperty("guarantor_type") VehicleRelationGuarantorType guarantorType,
            @JsonProperty("guarantor_resident_id") Long guarantorResidentId,
            @JsonProperty("guarantor_apartment_id") Long guarantorApartmentId,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            @JsonProperty("status") VehicleRelationStatus status,
            @JsonProperty("reason") String reason) {}
}
