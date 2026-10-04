package vn.edu.huit.smartparking.backend.vehicle.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerTransferRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleSummary;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleResidentRelationRepository;

@Service
public class VehicleManagementService {
    private static final String RELATION_ENTITY = "VEHICLE_RESIDENT_RELATION";

    private final VehicleRepository vehicleRepository;
    private final VehicleResidentRelationRepository relationRepository;
    private final ResidentRepository residentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final VehicleRightInvalidationService invalidationService;

    public VehicleManagementService(
            VehicleRepository vehicleRepository,
            VehicleResidentRelationRepository relationRepository,
            ResidentRepository residentRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            VehicleRightInvalidationService invalidationService) {
        this.vehicleRepository = vehicleRepository;
        this.relationRepository = relationRepository;
        this.residentRepository = residentRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.invalidationService = invalidationService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<VehicleSummary> list(String plateNumber, int page, int size) {
        validateQuery(plateNumber, page, size);
        PageRequest pageable = PageRequest.of(page, size);
        Page<Vehicle> vehicles = plateNumber == null
                ? vehicleRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : vehicleRepository.findAllByPlateNormalizedOrderByCreatedAtDescIdDesc(plateNumber, pageable);
        return new PagedResponse<>(vehicles.getContent().stream().map(this::summary).toList(),
                vehicles.getNumber(), vehicles.getSize(), vehicles.getTotalElements());
    }

    @Transactional
    public VehicleSummary detail(Long id, User actor) {
        Vehicle vehicle = requireVehicle(id);
        auditService.record("VEHICLE_DETAIL_READ", "VEHICLE", id.toString(), actor, null, null);
        return summary(vehicle);
    }

    @Transactional
    public VehicleRightDetail assignOwner(Long vehicleId, VehicleOwnerAssignmentRequest request, User actor) {
        validateAssignment(vehicleId, request);
        Resident resident = residentRepository.findByIdForUpdate(request.residentId())
                .orElseThrow(VehicleNotFoundException::new);
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(VehicleNotFoundException::new);
        requireOwnerEligible(resident);

        List<VehicleResidentRelation> overlaps = relationRepository.findActiveOverlapsForUpdate(
                vehicleId, request.validFrom(), request.validTo(), VehicleRelationStatus.ACTIVE);
        if (overlaps.stream().anyMatch(relation -> resident.getId().equals(relation.getResident().getId()))) {
            throw new VehicleRightOverlapException();
        }
        if (overlaps.stream().anyMatch(relation -> relation.getRelationType() == VehicleRelationType.OWNER)) {
            throw new VehicleOwnerConflictException();
        }

        LocalDateTime now = LocalDateTime.now();
        VehicleResidentRelation relation = new VehicleResidentRelation();
        relation.setVehicle(vehicle);
        relation.setResident(resident);
        relation.setRelationType(VehicleRelationType.OWNER);
        relation.setValidFrom(request.validFrom());
        relation.setValidTo(request.validTo());
        relation.setStatus(VehicleRelationStatus.ACTIVE);
        relation.setCreatedAt(now);
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        auditService.record("VEHICLE_OWNER_ASSIGNED", RELATION_ENTITY, saved.getId().toString(), actor, null,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    @Transactional
    public VehicleRightDetail transferOwner(Long vehicleId, VehicleOwnerTransferRequest request, User actor) {
        validateTransfer(vehicleId, request);
        VehicleResidentRelation discovered = relationRepository.findById(request.fromRelationId())
                .orElseThrow(VehicleOwnerRelationNotFoundException::new);
        if (discovered.getVehicle() == null || !vehicleId.equals(discovered.getVehicle().getId())) {
            throw new VehicleOwnerRelationNotFoundException();
        }
        Long sourceResidentId = discovered.getResident().getId();
        if (sourceResidentId.equals(request.toResidentId())) {
            throw new VehicleRelationStateConflictException();
        }

        List<Long> residentIds = java.util.stream.Stream.of(sourceResidentId, request.toResidentId()).sorted().toList();
        java.util.Map<Long, Resident> residents = new java.util.LinkedHashMap<>();
        for (Long residentId : residentIds) {
            Resident resident = residentRepository.findByIdForUpdate(residentId)
                    .orElseThrow(VehicleNotFoundException::new);
            residents.put(residentId, resident);
        }
        LocalDateTime effectiveAt = request.effectiveAt();
        List<Long> apartmentIds = invalidationService.lockOwnerLossApartmentContexts(
                vehicleId, sourceResidentId, effectiveAt);
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(VehicleNotFoundException::new);
        List<VehicleResidentRelation> lockedRelations = relationRepository.findAllActiveForVehicleForUpdate(
                vehicleId, VehicleRelationStatus.ACTIVE);
        VehicleResidentRelation source = lockedRelations.stream()
                .filter(relation -> request.fromRelationId().equals(relation.getId()))
                .findFirst().orElseThrow(VehicleRelationStateConflictException::new);
        if (source.getVehicle() == null || !vehicleId.equals(source.getVehicle().getId())
                || source.getResident() == null || !sourceResidentId.equals(source.getResident().getId())) {
            throw new VehicleRelationStateConflictException();
        }
        if (source.getRelationType() != VehicleRelationType.OWNER || source.getStatus() != VehicleRelationStatus.ACTIVE) {
            throw new VehicleRelationStateConflictException();
        }

        LocalDateTime commandTime = LocalDateTime.now();
        if (source.getValidFrom().isAfter(commandTime)
                || source.getValidTo() != null && !commandTime.isBefore(source.getValidTo())
                || !effectiveAt.isAfter(source.getValidFrom())
                || source.getValidTo() != null && effectiveAt.isAfter(source.getValidTo())) {
            throw new VehicleRelationStateConflictException();
        }
        Resident target = residents.get(request.toResidentId());
        requireOwnerEligible(target);

        List<VehicleResidentRelation> overlaps = lockedRelations.stream()
                .filter(relation -> relation.getValidTo() == null || relation.getValidTo().isAfter(effectiveAt))
                .filter(relation -> !request.fromRelationId().equals(relation.getId()))
                .toList();
        if (overlaps.stream().anyMatch(relation -> request.toResidentId().equals(relation.getResident().getId()))) {
            throw new VehicleRightOverlapException();
        }
        if (overlaps.stream().anyMatch(relation -> relation.getRelationType() == VehicleRelationType.OWNER)) {
            throw new VehicleOwnerConflictException();
        }

        if (effectiveAt.isAfter(commandTime)) {
            invalidationService.scheduleOwnerLoss(sourceResidentId, effectiveAt, request.reason(), actor,
                    apartmentIds, lockedRelations);
        } else {
            invalidationService.invalidateOwnerLoss(sourceResidentId, effectiveAt, request.reason(), actor,
                    apartmentIds, lockedRelations);
        }

        String oldData = auditData(source, null);
        source.setValidTo(effectiveAt);
        VehicleResidentRelation savedSource = relationRepository.saveAndFlush(source);
        auditService.record("VEHICLE_OWNER_TRANSFERRED_OUT", RELATION_ENTITY, savedSource.getId().toString(), actor,
                oldData, auditData(savedSource, request.reason()));

        VehicleResidentRelation successor = new VehicleResidentRelation();
        successor.setVehicle(vehicle);
        successor.setResident(target);
        successor.setRelationType(VehicleRelationType.OWNER);
        successor.setValidFrom(effectiveAt);
        successor.setStatus(VehicleRelationStatus.ACTIVE);
        successor.setCreatedAt(commandTime);
        VehicleResidentRelation savedSuccessor = relationRepository.saveAndFlush(successor);
        auditService.record("VEHICLE_OWNER_TRANSFERRED_IN", RELATION_ENTITY, savedSuccessor.getId().toString(), actor,
                null, auditData(savedSuccessor, request.reason()));
        return detail(savedSuccessor);
    }

    private Vehicle requireVehicle(Long id) {
        if (!positive(id)) {
            throw new VehicleInvalidRequestException();
        }
        return vehicleRepository.findById(id).orElseThrow(VehicleNotFoundException::new);
    }

    private void requireOwnerEligible(Resident resident) {
        if (resident.getStatus() == ResidentStatus.INACTIVE) {
            throw new VehicleStatusConflictException();
        }
    }

    private void validateQuery(String plateNumber, int page, int size) {
        if (plateNumber != null && (plateNumber.isBlank() || plateNumber.length() > 30)
                || page < 0 || size < 1 || size > 100) {
            throw new VehicleInvalidRequestException();
        }
    }

    private void validateAssignment(Long vehicleId, VehicleOwnerAssignmentRequest request) {
        if (!positive(vehicleId) || request == null || !positive(request.residentId()) || request.validFrom() == null
                || request.validTo() != null && !request.validTo().isAfter(request.validFrom())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new VehicleInvalidRequestException();
        }
    }

    private void validateTransfer(Long vehicleId, VehicleOwnerTransferRequest request) {
        if (!positive(vehicleId) || request == null || !positive(request.fromRelationId())
                || !positive(request.toResidentId()) || request.effectiveAt() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new VehicleInvalidRequestException();
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private VehicleSummary summary(Vehicle vehicle) {
        Long categoryId = vehicle.getVehicleCategory() == null ? null : vehicle.getVehicleCategory().getId();
        return new VehicleSummary(vehicle.getId(), vehicle.getPlateNumber(), categoryId,
                vehicle.getBrand(), vehicle.getStatus());
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
            return objectMapper.writeValueAsString(new VehicleRelationAuditData(
                    relation.getVehicle().getId(), relation.getResident().getId(), relation.getRelationType(),
                    relation.getValidFrom(), relation.getValidTo(), relation.getStatus(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize Vehicle OWNER audit data", exception);
        }
    }

    private record VehicleRelationAuditData(
            @JsonProperty("vehicle_id") Long vehicleId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("relation_type") VehicleRelationType relationType,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            VehicleRelationStatus status,
            String reason) {}
}
