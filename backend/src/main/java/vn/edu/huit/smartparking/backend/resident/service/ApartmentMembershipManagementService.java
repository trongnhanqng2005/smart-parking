package vn.edu.huit.smartparking.backend.resident.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;
import vn.edu.huit.smartparking.backend.audit.repository.AuditLogRepository;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipLifecycleRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipTransferRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipVoidRequest;
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

@Service
public class ApartmentMembershipManagementService {
    private static final String ENTITY_TYPE = "APARTMENT_MEMBERSHIP";
    private static final String LOCK_TRANSFER_MEMBERSHIPS = "SELECT id, apartment_id, resident_id, member_role, "
            + "valid_from, valid_to, status FROM apartment_memberships WHERE id = ? OR "
            + "(apartment_id = ? AND status = 'ACTIVE' AND (valid_to IS NULL OR valid_to > ?)) "
            + "ORDER BY id FOR UPDATE";

    private final ApartmentMembershipRepository membershipRepository;
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final VehicleRightInvalidationPort vehicleRightInvalidationPort;

    public ApartmentMembershipManagementService(
            ApartmentMembershipRepository membershipRepository,
            ResidentRepository residentRepository,
            ApartmentRepository apartmentRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            VehicleRightInvalidationPort vehicleRightInvalidationPort) {
        this.membershipRepository = membershipRepository;
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.vehicleRightInvalidationPort = vehicleRightInvalidationPort;
    }

    @Transactional
    public MembershipDetail add(MembershipCreateRequest request, User actor) {
        validateCreate(request);
        Resident resident = residentRepository.findByIdForUpdate(request.residentId())
                .orElseThrow(ResidentNotFoundException::new);
        Apartment apartment = apartmentRepository.findByIdForUpdate(request.apartmentId())
                .orElseThrow(ApartmentNotFoundException::new);
        requireActive(resident, apartment);

        List<ApartmentMembership> overlaps = membershipRepository.findActiveOverlaps(
                apartment.getId(), request.validFrom(), request.validTo(), MembershipStatus.ACTIVE);
        if (overlaps.stream().anyMatch(existing -> resident.getId().equals(existing.getResident().getId()))) {
            throw new MembershipOverlapException();
        }

        LocalDateTime now = LocalDateTime.now();
        ApartmentMembership membership = new ApartmentMembership();
        membership.setApartment(apartment);
        membership.setResident(resident);
        membership.setMemberRole(MembershipRole.MEMBER);
        membership.setValidFrom(request.validFrom());
        membership.setValidTo(request.validTo());
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setCreatedAt(now);
        ApartmentMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record("MEMBERSHIP_CREATED", ENTITY_TYPE, saved.getId().toString(), actor, null,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    @Transactional
    public MembershipDetail assignHouseholdHead(Long apartmentId, MembershipHeadAssignRequest request, User actor) {
        validateHeadAssignment(apartmentId, request);
        Resident resident = residentRepository.findByIdForUpdate(request.residentId())
                .orElseThrow(ResidentNotFoundException::new);
        Apartment apartment = apartmentRepository.findByIdForUpdate(apartmentId)
                .orElseThrow(ApartmentNotFoundException::new);
        requireActive(resident, apartment);

        List<ApartmentMembership> overlapping = membershipRepository.findActiveOverlaps(
                apartment.getId(), request.validFrom(), request.validTo(), MembershipStatus.ACTIVE);
        if (overlapping.stream().anyMatch(existing -> resident.getId().equals(existing.getResident().getId()))) {
            throw new MembershipOverlapException();
        }
        if (!membershipRepository.findActiveRoleOverlaps(apartment.getId(), MembershipRole.HOUSEHOLD_HEAD,
                request.validFrom(), request.validTo(), MembershipStatus.ACTIVE).isEmpty()) {
            throw new HouseholdHeadConflictException();
        }

        LocalDateTime now = LocalDateTime.now();
        ApartmentMembership membership = new ApartmentMembership();
        membership.setApartment(apartment);
        membership.setResident(resident);
        membership.setMemberRole(MembershipRole.HOUSEHOLD_HEAD);
        membership.setValidFrom(request.validFrom());
        membership.setValidTo(request.validTo());
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setCreatedAt(now);
        ApartmentMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record("HOUSEHOLD_HEAD_ASSIGNED", ENTITY_TYPE, saved.getId().toString(), actor, null,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MembershipDetail> list(Long apartmentId, Long residentId, int page, int size) {
        validateQuery(apartmentId, residentId, page, size);
        PageRequest pageable = PageRequest.of(page, size);
        Page<ApartmentMembership> memberships;
        if (apartmentId != null && residentId != null) {
            memberships = membershipRepository.findAllByApartment_IdAndResident_IdOrderByCreatedAtDescIdDesc(
                    apartmentId, residentId, pageable);
        } else if (apartmentId != null) {
            memberships = membershipRepository.findAllByApartment_IdOrderByCreatedAtDescIdDesc(apartmentId, pageable);
        } else if (residentId != null) {
            memberships = membershipRepository.findAllByResident_IdOrderByCreatedAtDescIdDesc(residentId, pageable);
        } else {
            memberships = membershipRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return new PagedResponse<>(memberships.getContent().stream().map(this::detail).toList(),
                memberships.getNumber(), memberships.getSize(), memberships.getTotalElements());
    }

    @Transactional
    public MembershipDetail detail(Long id, User actor) {
        ApartmentMembership membership = requireMembership(id);
        auditService.record("MEMBERSHIP_DETAIL_READ", ENTITY_TYPE, id.toString(), actor, null, null);
        return detail(membership);
    }

    @Transactional(readOnly = true)
    public PagedResponse<HistoryItem> history(Long id, int page, int size) {
        validateQuery(null, null, page, size);
        requireMembership(id);
        Page<AuditLog> logs = auditLogRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                ENTITY_TYPE, id.toString(), PageRequest.of(page, size));
        return new PagedResponse<>(logs.getContent().stream().map(this::historyItem).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    @Transactional
    public MembershipDetail end(Long id, MembershipLifecycleRequest request, User actor) {
        return changeLifecycle(id, request, MembershipStatus.INACTIVE, "MEMBERSHIP_ENDED", actor);
    }

    @Transactional
    public MembershipDetail revoke(Long id, MembershipLifecycleRequest request, User actor) {
        return changeLifecycle(id, request, MembershipStatus.REVOKED, "MEMBERSHIP_REVOKED", actor);
    }

    @Transactional
    public MembershipDetail voidMembership(Long id, MembershipVoidRequest request, User actor) {
        validateVoid(id, request);
        ApartmentMembership discovered = requireMembership(id);
        Resident resident = residentRepository.findByIdForUpdate(discovered.getResident().getId())
                .orElseThrow(ResidentNotFoundException::new);
        Apartment apartment = apartmentRepository.findByIdForUpdate(discovered.getApartment().getId())
                .orElseThrow(ApartmentNotFoundException::new);
        if (discovered.getStatus() == MembershipStatus.VOID) {
            throw new MembershipStateConflictException();
        }

        LocalDateTime commandTime = LocalDateTime.now();
        vehicleRightInvalidationPort.voidDependentVehicleRightsForMembership(
                new VehicleRightInvalidationPort.MembershipVoidSource(
                        id, apartment.getId(), resident.getId(), discovered.getMemberRole(),
                        discovered.getValidFrom(), discovered.getValidTo(), commandTime, request.reason()), actor);

        ApartmentMembership membership = membershipRepository.findByIdForUpdate(id)
                .orElseThrow(MembershipNotFoundException::new);
        if (!resident.getId().equals(membership.getResident().getId())
                || !apartment.getId().equals(membership.getApartment().getId())
                || membership.getStatus() != discovered.getStatus()
                || membership.getMemberRole() != discovered.getMemberRole()
                || !java.util.Objects.equals(discovered.getValidFrom(), membership.getValidFrom())
                || !java.util.Objects.equals(discovered.getValidTo(), membership.getValidTo())) {
            throw new MembershipConcurrentModificationException();
        }
        if (membership.getStatus() == MembershipStatus.VOID) {
            throw new MembershipStateConflictException();
        }

        String oldData = auditData(membership, membership.getLifecycleReason());
        membership.setStatus(MembershipStatus.VOID);
        membership.setLifecycleChangedAt(commandTime);
        membership.setLifecycleReason(request.reason());
        ApartmentMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record("MEMBERSHIP_VOIDED", ENTITY_TYPE, saved.getId().toString(), actor, oldData,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    @Transactional
    public MembershipDetail transferHouseholdHead(
            Long apartmentId, MembershipTransferRequest request, User actor) {
        validateTransfer(apartmentId, request);
        ApartmentMembership discovered = requireMembership(request.fromMembershipId());
        if (!apartmentId.equals(discovered.getApartment().getId())) {
            throw new MembershipNotFoundException();
        }
        Long fromResidentId = discovered.getResident().getId();
        Long toResidentId = request.toResidentId();
        if (fromResidentId.equals(toResidentId)) {
            throw new MembershipStateConflictException();
        }

        List<Long> residentIds = java.util.stream.Stream.of(fromResidentId, toResidentId).sorted().toList();
        Map<Long, Resident> lockedResidents = new java.util.LinkedHashMap<>();
        for (Long residentId : residentIds) {
            Resident resident = residentRepository.findByIdForUpdate(residentId)
                    .orElseThrow(ResidentNotFoundException::new);
            lockedResidents.put(residentId, resident);
        }
        Apartment apartment = apartmentRepository.findByIdForUpdate(apartmentId)
                .orElseThrow(ApartmentNotFoundException::new);
        LocalDateTime commandTime = LocalDateTime.now();
        VehicleRightInvalidationPort.MembershipAuthorityLoss authorityLoss =
                new VehicleRightInvalidationPort.MembershipAuthorityLoss(
                        apartmentId, fromResidentId, request.effectiveAt(), request.reason());
        if (request.effectiveAt().isAfter(commandTime)) {
            vehicleRightInvalidationPort.scheduleForMembershipLosses(List.of(authorityLoss), actor);
        } else {
            vehicleRightInvalidationPort.invalidateForMembershipLosses(List.of(authorityLoss), actor);
        }
        List<MembershipLockSnapshot> lockedMemberships = jdbcTemplate.query(LOCK_TRANSFER_MEMBERSHIPS,
                (row, rowNumber) -> new MembershipLockSnapshot(
                        row.getLong("id"), row.getLong("apartment_id"), row.getLong("resident_id"),
                        row.getString("member_role"), row.getTimestamp("valid_from").toLocalDateTime(),
                        localDateTime(row.getTimestamp("valid_to")), row.getString("status")),
                request.fromMembershipId(), apartmentId, request.effectiveAt());
        MembershipLockSnapshot sourceSnapshot = lockedMemberships.stream()
                .filter(membership -> request.fromMembershipId().equals(membership.id()))
                .findFirst().orElseThrow(MembershipNotFoundException::new);
        if (!apartmentId.equals(sourceSnapshot.apartmentId())) {
            throw new MembershipNotFoundException();
        }
        if (!fromResidentId.equals(sourceSnapshot.residentId())
                || !lockedResidents.containsKey(sourceSnapshot.residentId())) {
            throw new MembershipConcurrentModificationException();
        }
        Resident target = lockedResidents.get(toResidentId);
        if (apartment.getStatus() != ApartmentStatus.ACTIVE || target.getStatus() != ResidentStatus.ACTIVE) {
            throw new MembershipStatusConflictException();
        }
        LocalDateTime effectiveAt = request.effectiveAt();
        if (!MembershipRole.HOUSEHOLD_HEAD.name().equals(sourceSnapshot.memberRole())
                || !MembershipStatus.ACTIVE.name().equals(sourceSnapshot.status())
                || sourceSnapshot.validFrom().isAfter(commandTime)
                || sourceSnapshot.validTo() != null && !commandTime.isBefore(sourceSnapshot.validTo())
                || !effectiveAt.isAfter(sourceSnapshot.validFrom())
                || sourceSnapshot.validTo() != null && effectiveAt.isAfter(sourceSnapshot.validTo())) {
            throw new MembershipStateConflictException();
        }

        List<MembershipLockSnapshot> overlaps = lockedMemberships.stream()
                .filter(existing -> apartmentId.equals(existing.apartmentId())
                        && !request.fromMembershipId().equals(existing.id())
                        && MembershipStatus.ACTIVE.name().equals(existing.status())
                        && (existing.validTo() == null || existing.validTo().isAfter(effectiveAt)))
                .toList();
        if (overlaps.stream().anyMatch(existing -> toResidentId.equals(existing.residentId()))) {
            throw new MembershipOverlapException();
        }
        boolean anotherHeadOverlaps = overlaps.stream()
                .anyMatch(existing -> MembershipRole.HOUSEHOLD_HEAD.name().equals(existing.memberRole()));
        if (anotherHeadOverlaps) {
            throw new HouseholdHeadConflictException();
        }

        ApartmentMembership source = discovered;
        source.setMemberRole(MembershipRole.valueOf(sourceSnapshot.memberRole()));
        source.setValidFrom(sourceSnapshot.validFrom());
        source.setValidTo(sourceSnapshot.validTo());
        source.setStatus(MembershipStatus.ACTIVE);
        source.setLifecycleChangedAt(null);
        source.setLifecycleReason(null);
        String sourceOldData = auditData(source, null);
        source.setValidTo(effectiveAt);
        membershipRepository.saveAndFlush(source);
        auditService.record("HOUSEHOLD_HEAD_TRANSFERRED_OUT", ENTITY_TYPE, source.getId().toString(), actor,
                sourceOldData, auditData(source, request.reason()));

        ApartmentMembership transferred = new ApartmentMembership();
        transferred.setApartment(apartment);
        transferred.setResident(target);
        transferred.setMemberRole(MembershipRole.HOUSEHOLD_HEAD);
        transferred.setValidFrom(effectiveAt);
        transferred.setStatus(MembershipStatus.ACTIVE);
        transferred.setCreatedAt(commandTime);
        ApartmentMembership saved = membershipRepository.saveAndFlush(transferred);
        auditService.record("HOUSEHOLD_HEAD_TRANSFERRED_IN", ENTITY_TYPE, saved.getId().toString(), actor, null,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    private MembershipDetail changeLifecycle(
            Long id, MembershipLifecycleRequest request, MembershipStatus status, String action, User actor) {
        validateLifecycle(id, request);
        ApartmentMembership discovered = requireMembership(id);
        Resident resident = residentRepository.findByIdForUpdate(discovered.getResident().getId())
                .orElseThrow(ResidentNotFoundException::new);
        Apartment apartment = apartmentRepository.findByIdForUpdate(discovered.getApartment().getId())
                .orElseThrow(ApartmentNotFoundException::new);
        LocalDateTime commandTime = LocalDateTime.now();
        LocalDateTime effectiveAt = request.effectiveAt();
        if (discovered.getStatus() != MembershipStatus.ACTIVE
                || !effectiveAt.isAfter(discovered.getValidFrom()) || effectiveAt.isAfter(commandTime)
                || discovered.getValidTo() != null && effectiveAt.isAfter(discovered.getValidTo())) {
            throw new MembershipStateConflictException();
        }
        vehicleRightInvalidationPort.invalidateForMembershipLosses(
                List.of(new VehicleRightInvalidationPort.MembershipAuthorityLoss(
                        apartment.getId(), resident.getId(), effectiveAt, request.reason())), actor);
        ApartmentMembership membership = membershipRepository.findByIdForUpdate(id)
                .orElseThrow(MembershipNotFoundException::new);
        if (!resident.getId().equals(membership.getResident().getId())
                || !apartment.getId().equals(membership.getApartment().getId())
                || !java.util.Objects.equals(discovered.getValidFrom(), membership.getValidFrom())
                || !java.util.Objects.equals(discovered.getValidTo(), membership.getValidTo())) {
            throw new MembershipConcurrentModificationException();
        }
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new MembershipStateConflictException();
        }
        if (!effectiveAt.isAfter(membership.getValidFrom()) || effectiveAt.isAfter(commandTime)
                || membership.getValidTo() != null && effectiveAt.isAfter(membership.getValidTo())) {
            throw new MembershipStateConflictException();
        }
        String oldData = auditData(membership, membership.getLifecycleReason());
        membership.setValidTo(effectiveAt);
        membership.setStatus(status);
        membership.setLifecycleChangedAt(commandTime);
        membership.setLifecycleReason(request.reason());
        ApartmentMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record(action, ENTITY_TYPE, saved.getId().toString(), actor, oldData,
                auditData(saved, request.reason()));
        return detail(saved);
    }

    private void requireActive(Resident resident, Apartment apartment) {
        if (resident.getStatus() != ResidentStatus.ACTIVE || apartment.getStatus() != ApartmentStatus.ACTIVE) {
            throw new MembershipStatusConflictException();
        }
    }

    private void validateCreate(MembershipCreateRequest request) {
        if (request == null || !positive(request.apartmentId()) || !positive(request.residentId())
                || request.memberRole() != MembershipRole.MEMBER || request.validFrom() == null
                || request.validTo() != null && !request.validTo().isAfter(request.validFrom())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new MembershipInvalidRequestException();
        }
    }

    private void validateHeadAssignment(Long apartmentId, MembershipHeadAssignRequest request) {
        if (!positive(apartmentId) || request == null || !positive(request.residentId())
                || request.validFrom() == null
                || request.validTo() != null && !request.validTo().isAfter(request.validFrom())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new MembershipInvalidRequestException();
        }
    }

    private void validateLifecycle(Long id, MembershipLifecycleRequest request) {
        if (!positive(id) || request == null || request.effectiveAt() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new MembershipInvalidRequestException();
        }
    }

    private void validateVoid(Long id, MembershipVoidRequest request) {
        if (!positive(id) || request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new MembershipInvalidRequestException();
        }
    }

    private void validateTransfer(Long apartmentId, MembershipTransferRequest request) {
        if (!positive(apartmentId) || request == null || !positive(request.fromMembershipId())
                || !positive(request.toResidentId())
                || request.effectiveAt() == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new MembershipInvalidRequestException();
        }
    }

    private void validateQuery(Long apartmentId, Long residentId, int page, int size) {
        if (apartmentId != null && !positive(apartmentId) || residentId != null && !positive(residentId)
                || page < 0 || size < 1 || size > 100) {
            throw new MembershipInvalidRequestException();
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private ApartmentMembership requireMembership(Long id) {
        if (!positive(id)) {
            throw new MembershipInvalidRequestException();
        }
        return membershipRepository.findById(id).orElseThrow(MembershipNotFoundException::new);
    }

    private MembershipDetail detail(ApartmentMembership membership) {
        return new MembershipDetail(membership.getId(), membership.getApartment().getId(),
                membership.getResident().getId(), membership.getMemberRole(), membership.getValidFrom(),
                membership.getValidTo(), membership.getStatus(), membership.getLifecycleChangedAt(),
                membership.getLifecycleReason(), membership.getCreatedAt());
    }

    private HistoryItem historyItem(AuditLog log) {
        Long actorUserId = log.getActorUser() == null ? null : log.getActorUser().getId();
        return new HistoryItem(log.getAction(), log.getCreatedAt(), historyReason(log.getNewData()),
                actorUserId, Long.valueOf(log.getEntityId()));
    }

    private String historyReason(String newData) {
        if (newData == null) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(newData, new TypeReference<>() {});
            Object reason = data.get("reason");
            return reason instanceof String value ? value : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String auditData(ApartmentMembership membership, String reason) {
        try {
            return objectMapper.writeValueAsString(new MembershipAuditData(
                    membership.getApartment().getId(), membership.getResident().getId(), membership.getMemberRole(),
                    membership.getValidFrom(), membership.getValidTo(), membership.getStatus(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize membership audit data", exception);
        }
    }

    private record MembershipAuditData(
            @JsonProperty("apartment_id") Long apartmentId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("member_role") MembershipRole memberRole,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            MembershipStatus status,
            String reason) {}

    private record MembershipLockSnapshot(
            Long id,
            Long apartmentId,
            Long residentId,
            String memberRole,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String status) {}
}
