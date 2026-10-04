package vn.edu.huit.smartparking.backend.vehicle.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;
import vn.edu.huit.smartparking.backend.audit.repository.AuditLogRepository;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentMembershipRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.dto.AuthorizedUserGrantRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleResidentRelationRepository;

@Service
public class AuthorizedUserGrantService {
    private static final String RELATION_ENTITY = "VEHICLE_RESIDENT_RELATION";
    private static final int MAX_LOCK_SET_RETRIES = 2;

    private final VehicleRepository vehicleRepository;
    private final VehicleResidentRelationRepository relationRepository;
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final ApartmentMembershipRepository membershipRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AuthorizedUserGrantService(
            VehicleRepository vehicleRepository,
            VehicleResidentRelationRepository relationRepository,
            ResidentRepository residentRepository,
            ApartmentRepository apartmentRepository,
            ApartmentMembershipRepository membershipRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.vehicleRepository = vehicleRepository;
        this.relationRepository = relationRepository;
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.membershipRepository = membershipRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public VehicleRightDetail grantAuthorizedUser(Long vehicleId, AuthorizedUserGrantRequest request, User actor) {
        validateGrant(vehicleId, request);
        TreeSet<Long> residentIds = discoverResidentLockSet(vehicleId, request);
        for (int attempt = 0; attempt <= MAX_LOCK_SET_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> grantInTransaction(vehicleId, request, actor, residentIds));
            } catch (GrantLockSetChangedException exception) {
                residentIds.addAll(exception.missingResidentIds());
                if (attempt == MAX_LOCK_SET_RETRIES) {
                    throw new VehicleConcurrentModificationException();
                }
            }
        }
        throw new VehicleConcurrentModificationException();
    }

    @Transactional(readOnly = true)
    public PagedResponse<VehicleRightDetail> list(
            Long vehicleId, Long residentId, VehicleRelationType relationType, int page, int size) {
        validateQuery(vehicleId, residentId, page, size);
        Page<VehicleResidentRelation> relations = relationRepository.findByFilters(vehicleId, residentId,
                relationType, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PagedResponse<>(relations.getContent().stream().map(this::detail).toList(),
                relations.getNumber(), relations.getSize(), relations.getTotalElements());
    }

    @Transactional
    public VehicleRightDetail detail(Long id, User actor) {
        VehicleResidentRelation relation = requireRelation(id);
        auditService.record("VEHICLE_RIGHT_DETAIL_READ", RELATION_ENTITY, id.toString(), actor, null, null);
        return detail(relation);
    }

    @Transactional(readOnly = true)
    public PagedResponse<HistoryItem> history(Long id, int page, int size) {
        validateHistoryQuery(id, page, size);
        VehicleResidentRelation relation = requireRelation(id);
        Page<AuditLog> logs = auditLogRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                RELATION_ENTITY, relation.getId().toString(), PageRequest.of(page, size));
        return new PagedResponse<>(logs.getContent().stream().map(this::historyItem).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    private TreeSet<Long> discoverResidentLockSet(Long vehicleId, AuthorizedUserGrantRequest request) {
        TreeSet<Long> residentIds = new TreeSet<>();
        residentIds.add(request.residentId());
        residentIds.add(request.guarantorResidentId());
        residentIds.addAll(relationRepository.findActiveOverlapResidentIds(
                vehicleId, request.validFrom(), request.validTo(), VehicleRelationStatus.ACTIVE));
        return residentIds;
    }

    private VehicleRightDetail grantInTransaction(
            Long vehicleId, AuthorizedUserGrantRequest request, User actor, TreeSet<Long> residentLockSet) {
        Map<Long, Resident> residents = new LinkedHashMap<>();
        for (Long residentId : residentLockSet) {
            Resident resident = residentRepository.findByIdForUpdate(residentId)
                    .orElseThrow(VehicleNotFoundException::new);
            residents.put(residentId, resident);
        }
        Resident authorizedResident = residents.get(request.residentId());
        Resident guarantorResident = residents.get(request.guarantorResidentId());
        if (authorizedResident == null || guarantorResident == null) {
            throw new VehicleNotFoundException();
        }
        requireActiveResident(authorizedResident);
        requireActiveResident(guarantorResident);

        Apartment apartment = null;
        if (request.guarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD) {
            apartment = apartmentRepository.findByIdForUpdate(request.guarantorApartmentId())
                    .orElseThrow(VehicleNotFoundException::new);
            if (apartment.getStatus() != ApartmentStatus.ACTIVE) {
                throw new VehicleStatusConflictException();
            }
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
        List<VehicleResidentRelation> overlappingRelations = relationRepository.findActiveOverlapsForUpdate(
                vehicleId, request.validFrom(), request.validTo(), VehicleRelationStatus.ACTIVE);
        TreeSet<Long> missingResidents = new TreeSet<>();
        overlappingRelations.stream().map(relation -> relation.getResident().getId())
                .filter(residentId -> !residents.containsKey(residentId)).forEach(missingResidents::add);
        if (!missingResidents.isEmpty()) {
            throw new GrantLockSetChangedException(missingResidents);
        }

        if (overlappingRelations.stream()
                .anyMatch(relation -> request.residentId().equals(relation.getResident().getId()))) {
            throw new VehicleRightOverlapException();
        }
        LocalDateTime validFrom = request.validFrom();
        List<VehicleResidentRelation> effectiveOwners = overlappingRelations.stream()
                .filter(relation -> relation.getRelationType() == VehicleRelationType.OWNER
                        && effectiveAt(relation.getValidFrom(), relation.getValidTo(), validFrom))
                .toList();
        if (effectiveOwners.size() > 1) {
            throw new VehicleOwnerConflictException();
        }
        if (effectiveOwners.isEmpty()) {
            throw new GuarantorChainConflictException();
        }
        VehicleResidentRelation owner = effectiveOwners.getFirst();
        requireActiveResident(owner.getResident());
        if (!coversInterval(owner.getValidFrom(), owner.getValidTo(), request.validFrom(), request.validTo())) {
            throw new GuarantorChainConflictException();
        }

        if (request.guarantorType() == VehicleRelationGuarantorType.OWNER) {
            if (!owner.getResident().getId().equals(request.guarantorResidentId())) {
                throw new GuarantorChainConflictException();
            }
        } else {
            if (apartment == null) {
                throw new VehicleInvalidRequestException();
            }
            List<ApartmentMembership> headMemberships = membershipRepository.findEffectiveForUpdate(
                    apartment.getId(), request.guarantorResidentId(), validFrom, MembershipStatus.ACTIVE);
            boolean isEffectiveHead = headMemberships.stream()
                    .anyMatch(membership -> membership.getMemberRole() == MembershipRole.HOUSEHOLD_HEAD
                            && coversInterval(membership.getValidFrom(), membership.getValidTo(),
                                    request.validFrom(), request.validTo()));
            if (!isEffectiveHead) {
                throw new GuarantorChainConflictException();
            }
            Long ownerResidentId = owner.getResident().getId();
            List<ApartmentMembership> ownerMemberships = membershipRepository.findEffectiveForUpdate(
                    apartment.getId(), ownerResidentId, validFrom, MembershipStatus.ACTIVE);
            boolean ownerIsMemberForWholeGrant = ownerMemberships.stream()
                    .anyMatch(membership -> coversInterval(membership.getValidFrom(), membership.getValidTo(),
                            request.validFrom(), request.validTo()));
            if (!ownerIsMemberForWholeGrant) {
                throw new GuarantorChainConflictException();
            }
        }

        VehicleResidentRelation grant = new VehicleResidentRelation();
        grant.setVehicle(vehicle);
        grant.setResident(authorizedResident);
        grant.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        grant.setGuarantorType(request.guarantorType());
        grant.setGuarantorResident(guarantorResident);
        grant.setGuarantorApartment(apartment);
        grant.setValidFrom(request.validFrom());
        grant.setValidTo(request.validTo());
        grant.setStatus(VehicleRelationStatus.ACTIVE);
        grant.setCreatedAt(LocalDateTime.now());
        VehicleResidentRelation saved = relationRepository.saveAndFlush(grant);
        auditService.record("VEHICLE_AUTHORIZED_USER_GRANTED", RELATION_ENTITY, saved.getId().toString(), actor,
                null, auditData(saved, request.reason()));
        return detail(saved);
    }

    private void requireActiveResident(Resident resident) {
        if (resident.getStatus() != ResidentStatus.ACTIVE) {
            throw new VehicleStatusConflictException();
        }
    }

    private boolean effectiveAt(LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }

    private boolean coversInterval(
            LocalDateTime sourceValidFrom,
            LocalDateTime sourceValidTo,
            LocalDateTime grantValidFrom,
            LocalDateTime grantValidTo) {
        return !sourceValidFrom.isAfter(grantValidFrom)
                && (sourceValidTo == null || grantValidTo != null && !grantValidTo.isAfter(sourceValidTo));
    }

    private void validateGrant(Long vehicleId, AuthorizedUserGrantRequest request) {
        if (!positive(vehicleId) || request == null || !vehicleId.equals(request.vehicleId())
                || !positive(request.residentId())
                || request.guarantorType() == null || !positive(request.guarantorResidentId())
                || request.validFrom() == null
                || request.validTo() != null && !request.validTo().isAfter(request.validFrom())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500
                || request.guarantorType() == VehicleRelationGuarantorType.OWNER
                        && request.guarantorApartmentId() != null
                || request.guarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                        && !positive(request.guarantorApartmentId())) {
            throw new VehicleInvalidRequestException();
        }
    }

    private void validateQuery(Long vehicleId, Long residentId, int page, int size) {
        if (vehicleId != null && !positive(vehicleId) || residentId != null && !positive(residentId)
                || page < 0 || size < 1 || size > 100) {
            throw new VehicleInvalidRequestException();
        }
    }

    private void validateHistoryQuery(Long id, int page, int size) {
        if (!positive(id) || page < 0 || size < 1 || size > 100) {
            throw new VehicleInvalidRequestException();
        }
    }

    private VehicleResidentRelation requireRelation(Long id) {
        if (!positive(id)) {
            throw new VehicleInvalidRequestException();
        }
        return relationRepository.findById(id).orElseThrow(VehicleOwnerRelationNotFoundException::new);
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private VehicleRightDetail detail(VehicleResidentRelation relation) {
        return new VehicleRightDetail(relation.getId(), relation.getVehicle().getId(), relation.getResident().getId(),
                relation.getRelationType(), relation.getGuarantorType(),
                relation.getGuarantorResident() == null ? null : relation.getGuarantorResident().getId(),
                relation.getGuarantorApartment() == null ? null : relation.getGuarantorApartment().getId(),
                relation.getValidFrom(), relation.getValidTo(), relation.getStatus(),
                relation.getLifecycleChangedAt(), relation.getLifecycleReason(), relation.getCreatedAt());
    }

    private HistoryItem historyItem(AuditLog log) {
        Long actorUserId = log.getActorUser() == null ? null : log.getActorUser().getId();
        return new HistoryItem(log.getAction(), log.getCreatedAt(), historyReason(log.getNewData()),
                actorUserId, Long.valueOf(log.getEntityId()));
    }

    private String historyReason(String data) {
        if (data == null) {
            return null;
        }
        try {
            Map<String, Object> fields = objectMapper.readValue(data, new TypeReference<>() {});
            Object reason = fields.get("reason");
            return reason instanceof String value ? value : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private String auditData(VehicleResidentRelation relation, String reason) {
        try {
            return objectMapper.writeValueAsString(new AuthorizedUserAuditData(
                    relation.getVehicle().getId(), relation.getResident().getId(), relation.getRelationType(),
                    relation.getGuarantorType(), relation.getGuarantorResident().getId(),
                    relation.getGuarantorApartment() == null ? null : relation.getGuarantorApartment().getId(),
                    relation.getValidFrom(), relation.getValidTo(), relation.getStatus(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize authorized-user audit data", exception);
        }
    }

    private record AuthorizedUserAuditData(
            @JsonProperty("vehicle_id") Long vehicleId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("relation_type") VehicleRelationType relationType,
            @JsonProperty("guarantor_type") VehicleRelationGuarantorType guarantorType,
            @JsonProperty("guarantor_resident_id") Long guarantorResidentId,
            @JsonProperty("guarantor_apartment_id") Long guarantorApartmentId,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            VehicleRelationStatus status,
            String reason) {}

    private static class GrantLockSetChangedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final java.util.Set<Long> missingResidentIds;

        private GrantLockSetChangedException(java.util.Set<Long> missingResidentIds) {
            this.missingResidentIds = java.util.Set.copyOf(missingResidentIds);
        }

        private java.util.Set<Long> missingResidentIds() {
            return missingResidentIds;
        }
    }
}
