package vn.edu.huit.smartparking.backend.resident.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentReactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipEndRequest;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.MembershipAuthorityLoss;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class ApartmentStatusTransactionService {
    private static final String MEMBERSHIP_ENTITY = "APARTMENT_MEMBERSHIP";
    private static final String DISCOVER_EFFECTIVE_MEMBERSHIPS = "SELECT id, apartment_id, resident_id, member_role, "
            + "valid_from, valid_to, status, lifecycle_changed_at, lifecycle_reason FROM apartment_memberships "
            + "WHERE apartment_id = ? AND status = 'ACTIVE' AND valid_from <= ? "
            + "AND (valid_to IS NULL OR valid_to > ?) ORDER BY id";
    private static final String LOCK_EFFECTIVE_MEMBERSHIPS = DISCOVER_EFFECTIVE_MEMBERSHIPS + " FOR UPDATE";
    private static final String END_EFFECTIVE_MEMBERSHIP = "UPDATE apartment_memberships "
            + "SET valid_to = ?, status = 'INACTIVE', lifecycle_changed_at = ?, lifecycle_reason = ? "
            + "WHERE id = ? AND apartment_id = ? AND resident_id = ? AND member_role = ? AND status = 'ACTIVE' "
            + "AND valid_from = ? AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)";

    private final ApartmentRepository apartmentRepository;
    private final ResidentRepository residentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final VehicleRightInvalidationPort vehicleRightInvalidationPort;

    public ApartmentStatusTransactionService(
            ApartmentRepository apartmentRepository,
            ResidentRepository residentRepository,
            JdbcTemplate jdbcTemplate,
            AuditService auditService,
            ObjectMapper objectMapper,
            VehicleRightInvalidationPort vehicleRightInvalidationPort) {
        this.apartmentRepository = apartmentRepository;
        this.residentRepository = residentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.vehicleRightInvalidationPort = vehicleRightInvalidationPort;
    }

    @Transactional
    public ApartmentDetail deactivate(Long id, ApartmentDeactivationRequest request, User actor) {
        validateDeactivation(id, request);
        LocalDateTime commandTime = LocalDateTime.now();
        List<MembershipLifecycleRow> discoveredMemberships = jdbcTemplate.query(
                DISCOVER_EFFECTIVE_MEMBERSHIPS, this::membershipLifecycleRow,
                id, Timestamp.valueOf(commandTime), Timestamp.valueOf(commandTime));
        List<Long> discoveredResidentIds = discoveredMemberships.stream()
                .map(MembershipLifecycleRow::residentId).distinct().sorted().toList();
        Set<Long> lockedResidentIds = new LinkedHashSet<>();
        for (Long residentId : discoveredResidentIds) {
            residentRepository.findByIdForUpdate(residentId).orElseThrow(ResidentNotFoundException::new);
            lockedResidentIds.add(residentId);
        }

        Apartment apartment = apartmentRepository.findByIdForUpdate(id)
                .orElseThrow(ApartmentNotFoundException::new);
        if (apartment.getStatus() != ApartmentStatus.ACTIVE) {
            throw new ApartmentStatusConflictException();
        }

        List<MembershipLifecycleRow> currentMemberships = jdbcTemplate.query(
                DISCOVER_EFFECTIVE_MEMBERSHIPS, this::membershipLifecycleRow,
                id, Timestamp.valueOf(commandTime), Timestamp.valueOf(commandTime));
        Set<Long> currentResidentIds = currentMemberships.stream()
                .map(MembershipLifecycleRow::residentId).collect(Collectors.toSet());
        if (!lockedResidentIds.containsAll(currentResidentIds)) {
            throw new MembershipDiscoveryChangedException();
        }
        Map<Long, MembershipEndRequest> requestedEnds = requestedEnds(request.membershipEnds());
        Set<Long> currentMembershipIds = currentMemberships.stream()
                .map(MembershipLifecycleRow::id).collect(Collectors.toSet());
        if (!currentMembershipIds.equals(requestedEnds.keySet())) {
            throw new ApartmentStatusConflictException();
        }
        for (MembershipLifecycleRow membership : currentMemberships) {
            validateEnd(membership, requestedEnds.get(membership.id()), commandTime);
        }

        List<MembershipAuthorityLoss> losses = currentMemberships.stream()
                .map(membership -> new MembershipAuthorityLoss(membership.apartmentId(), membership.residentId(),
                        requestedEnds.get(membership.id()).effectiveAt(), requestedEnds.get(membership.id()).reason()))
                .toList();
        vehicleRightInvalidationPort.invalidateForMembershipLosses(losses, actor);

        currentMemberships = jdbcTemplate.query(LOCK_EFFECTIVE_MEMBERSHIPS, this::membershipLifecycleRow,
                id, Timestamp.valueOf(commandTime), Timestamp.valueOf(commandTime));
        currentResidentIds = currentMemberships.stream()
                .map(MembershipLifecycleRow::residentId).collect(Collectors.toSet());
        if (!lockedResidentIds.containsAll(currentResidentIds)) {
            throw new MembershipDiscoveryChangedException();
        }
        currentMembershipIds = currentMemberships.stream()
                .map(MembershipLifecycleRow::id).collect(Collectors.toSet());
        if (!currentMembershipIds.equals(requestedEnds.keySet())) {
            throw new ApartmentStatusConflictException();
        }
        for (MembershipLifecycleRow membership : currentMemberships) {
            validateEnd(membership, requestedEnds.get(membership.id()), commandTime);
        }

        List<Long> endedMembershipIds = currentMemberships.stream().map(MembershipLifecycleRow::id).toList();
        for (MembershipLifecycleRow membership : currentMemberships) {
            MembershipEndRequest endRequest = requestedEnds.get(membership.id());
            String oldData = membershipAuditData(membership, membership.lifecycleReason());
            int changedRows = jdbcTemplate.update(END_EFFECTIVE_MEMBERSHIP,
                    Timestamp.valueOf(endRequest.effectiveAt()), Timestamp.valueOf(commandTime), endRequest.reason(),
                    membership.id(), membership.apartmentId(), membership.residentId(), membership.memberRole(),
                    Timestamp.valueOf(membership.validFrom()), Timestamp.valueOf(commandTime),
                    Timestamp.valueOf(commandTime));
            if (changedRows != 1) {
                throw new ApartmentConcurrentModificationException();
            }
            auditService.record("MEMBERSHIP_ENDED", MEMBERSHIP_ENTITY, membership.id().toString(), actor,
                    oldData, membershipAuditData(membership, MembershipStatus.INACTIVE,
                            endRequest.effectiveAt(), commandTime, endRequest.reason()));
        }

        String oldApartmentData = apartmentAuditData(apartment.getStatus(), null, List.of());
        apartment.setStatus(ApartmentStatus.INACTIVE);
        apartment.setUpdatedAt(commandTime);
        Apartment saved = apartmentRepository.saveAndFlush(apartment);
        auditService.record("APARTMENT_DEACTIVATED", "APARTMENT", id.toString(), actor, oldApartmentData,
                apartmentAuditData(saved.getStatus(), request.reason(), endedMembershipIds));
        return detail(saved);
    }

    @Transactional
    public ApartmentDetail reactivate(Long id, ApartmentReactivationRequest request, User actor) {
        validateId(id);
        if (request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new ApartmentInvalidRequestException();
        }
        Apartment apartment = apartmentRepository.findByIdForUpdate(id)
                .orElseThrow(ApartmentNotFoundException::new);
        if (apartment.getStatus() != ApartmentStatus.INACTIVE) {
            throw new ApartmentStatusConflictException();
        }
        String oldData = apartmentAuditData(apartment.getStatus(), null, List.of());
        LocalDateTime now = LocalDateTime.now();
        apartment.setStatus(ApartmentStatus.ACTIVE);
        apartment.setUpdatedAt(now);
        Apartment saved = apartmentRepository.saveAndFlush(apartment);
        auditService.record("APARTMENT_REACTIVATED", "APARTMENT", id.toString(), actor, oldData,
                apartmentAuditData(saved.getStatus(), request.reason(), List.of()));
        return detail(saved);
    }

    private Map<Long, MembershipEndRequest> requestedEnds(List<MembershipEndRequest> requests) {
        Map<Long, MembershipEndRequest> result = new LinkedHashMap<>();
        if (requests == null) {
            return result;
        }
        for (MembershipEndRequest request : requests) {
            if (request == null || request.membershipId() == null || request.membershipId() <= 0
                    || request.effectiveAt() == null || request.reason() == null || request.reason().isBlank()
                    || request.reason().length() > 500
                    || result.putIfAbsent(request.membershipId(), request) != null) {
                throw new ApartmentInvalidRequestException();
            }
        }
        return result;
    }

    private void validateDeactivation(Long id, ApartmentDeactivationRequest request) {
        validateId(id);
        if (request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new ApartmentInvalidRequestException();
        }
        requestedEnds(request.membershipEnds());
    }

    private void validateEnd(MembershipLifecycleRow membership, MembershipEndRequest request, LocalDateTime commandTime) {
        if (request == null || !request.effectiveAt().isAfter(membership.validFrom())
                || request.effectiveAt().isAfter(commandTime)
                || membership.validTo() != null && request.effectiveAt().isAfter(membership.validTo())) {
            throw new MembershipStateConflictException();
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ApartmentInvalidRequestException();
        }
    }

    private MembershipLifecycleRow membershipLifecycleRow(ResultSet row, int rowNumber) throws SQLException {
        return new MembershipLifecycleRow(row.getLong("id"), row.getLong("apartment_id"),
                row.getLong("resident_id"), row.getString("member_role"),
                row.getTimestamp("valid_from").toLocalDateTime(), localDateTime(row.getTimestamp("valid_to")),
                row.getString("status"), localDateTime(row.getTimestamp("lifecycle_changed_at")),
                row.getString("lifecycle_reason"));
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String membershipAuditData(MembershipLifecycleRow membership, String reason) {
        return membershipAuditData(membership, MembershipStatus.valueOf(membership.status()),
                membership.validTo(), membership.lifecycleChangedAt(), reason);
    }

    private String membershipAuditData(
            MembershipLifecycleRow membership,
            MembershipStatus status,
            LocalDateTime validTo,
            LocalDateTime lifecycleChangedAt,
            String reason) {
        return serialize(new MembershipAuditData(membership.apartmentId(), membership.residentId(),
                MembershipRole.valueOf(membership.memberRole()), membership.validFrom(), validTo, status,
                lifecycleChangedAt, reason));
    }

    private String apartmentAuditData(ApartmentStatus status, String reason, List<Long> endedMembershipIds) {
        return serialize(new ApartmentAuditData(status, reason, endedMembershipIds));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize Apartment lifecycle audit data", exception);
        }
    }

    private ApartmentDetail detail(Apartment apartment) {
        return new ApartmentDetail(apartment.getId(), apartment.getBuilding(), apartment.getApartmentCode(),
                apartment.getFloorNo(), apartment.getStatus(), apartment.getCreatedAt(), apartment.getUpdatedAt());
    }

    private record MembershipAuditData(
            @JsonProperty("apartment_id") Long apartmentId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("member_role") MembershipRole memberRole,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            MembershipStatus status,
            @JsonProperty("lifecycle_changed_at") LocalDateTime lifecycleChangedAt,
            String reason) {}

    private record MembershipLifecycleRow(
            Long id,
            Long apartmentId,
            Long residentId,
            String memberRole,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String status,
            LocalDateTime lifecycleChangedAt,
            String lifecycleReason) {}

    private record ApartmentAuditData(
            ApartmentStatus status,
            String reason,
            @JsonProperty("ended_membership_ids") List<Long> endedMembershipIds) {}

    static class MembershipDiscoveryChangedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
