package vn.edu.huit.smartparking.backend.resident.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;
import vn.edu.huit.smartparking.backend.audit.repository.AuditLogRepository;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class ApartmentManagementService {
    private static final String IDENTITY_CONSTRAINT = "uk_apartments_normalized_identity";

    private final ApartmentRepository apartmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ApartmentManagementService(
            ApartmentRepository apartmentRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.apartmentRepository = apartmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApartmentDetail create(ApartmentCreateRequest request, User actor) {
        byte[] buildingKey = normalizedFilterKey(request.building(), true);
        byte[] apartmentCodeKey = normalizedFilterKey(request.apartmentCode(), false);
        if (apartmentRepository.findByBuildingKeyAndApartmentCodeKey(buildingKey, apartmentCodeKey).isPresent()) {
            throw new ApartmentIdentityConflictException();
        }

        LocalDateTime now = LocalDateTime.now();
        Apartment apartment = new Apartment();
        apartment.setBuilding(request.building());
        apartment.setApartmentCode(request.apartmentCode());
        apartment.setFloorNo(request.floorNo());
        apartment.setStatus(ApartmentStatus.ACTIVE);
        apartment.setCreatedAt(now);
        apartment.setUpdatedAt(now);

        try {
            Apartment saved = apartmentRepository.saveAndFlush(apartment);
            auditService.record("APARTMENT_CREATED", "APARTMENT", saved.getId().toString(), actor, null,
                    auditData(saved, null));
            return detail(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isIdentityConflict(exception)) {
                throw new ApartmentIdentityConflictException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApartmentSummary> list(String building, String apartmentCode, int page, int size) {
        validatePagination(page, size);
        byte[] buildingKey = filterKey(building, true);
        byte[] apartmentCodeKey = filterKey(apartmentCode, false);
        PageRequest pageable = PageRequest.of(page, size);
        Page<Apartment> apartments;
        if (buildingKey != null && apartmentCodeKey != null) {
            apartments = apartmentRepository.findAllByBuildingKeyAndApartmentCodeKeyOrderByCreatedAtDescIdDesc(
                    buildingKey, apartmentCodeKey, pageable);
        } else if (buildingKey != null) {
            apartments = apartmentRepository.findAllByBuildingKeyOrderByCreatedAtDescIdDesc(buildingKey, pageable);
        } else if (apartmentCodeKey != null) {
            apartments = apartmentRepository.findAllByApartmentCodeKeyOrderByCreatedAtDescIdDesc(
                    apartmentCodeKey, pageable);
        } else {
            apartments = apartmentRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return new PagedResponse<>(apartments.getContent().stream().map(this::summary).toList(),
                apartments.getNumber(), apartments.getSize(), apartments.getTotalElements());
    }

    @Transactional
    public ApartmentDetail detail(Long id, User actor) {
        Apartment apartment = requireApartment(id);
        auditService.record("APARTMENT_DETAIL_READ", "APARTMENT", apartment.getId().toString(), actor, null, null);
        return detail(apartment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<HistoryItem> history(Long id, int page, int size) {
        validatePagination(page, size);
        Apartment apartment = requireApartment(id);
        Page<AuditLog> logs = auditLogRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                "APARTMENT", apartment.getId().toString(), PageRequest.of(page, size));
        List<HistoryItem> items = logs.getContent().stream().map(this::historyItem).toList();
        return new PagedResponse<>(items, logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    @Transactional
    public ApartmentDetail correct(Long id, ApartmentCorrectionRequest request, User actor) {
        validateCorrection(request);
        if (id == null || id <= 0) {
            throw new ApartmentInvalidRequestException();
        }
        Apartment apartment = apartmentRepository.findByIdForUpdate(id)
                .orElseThrow(ApartmentNotFoundException::new);
        String building = request.hasBuilding() ? request.building() : apartment.getBuilding();
        String apartmentCode = request.hasApartmentCode() ? request.apartmentCode() : apartment.getApartmentCode();
        byte[] buildingKey = normalizedFilterKey(building, true);
        byte[] apartmentCodeKey = normalizedFilterKey(apartmentCode, false);
        boolean identityChanged = !Arrays.equals(apartment.getBuildingKey(), buildingKey)
                || !Arrays.equals(apartment.getApartmentCodeKey(), apartmentCodeKey);
        if (identityChanged && (request.reason() == null || request.reason().isBlank())) {
            throw new ApartmentInvalidRequestException();
        }
        apartmentRepository.findByBuildingKeyAndApartmentCodeKey(buildingKey, apartmentCodeKey)
                .filter(existing -> !id.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new ApartmentIdentityConflictException();
                });

        String oldData = auditData(apartment, null);
        if (request.hasBuilding()) {
            apartment.setBuilding(request.building());
        }
        if (request.hasApartmentCode()) {
            apartment.setApartmentCode(request.apartmentCode());
        }
        if (request.hasFloorNo()) {
            apartment.setFloorNo(request.floorNo());
        }
        apartment.setUpdatedAt(LocalDateTime.now());

        try {
            Apartment saved = apartmentRepository.saveAndFlush(apartment);
            auditService.record("APARTMENT_CORRECTED", "APARTMENT", saved.getId().toString(), actor,
                    oldData, auditData(saved, request.reason()));
            return detail(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isIdentityConflict(exception)) {
                throw new ApartmentIdentityConflictException();
            }
            throw exception;
        }
    }

    private void validateCorrection(ApartmentCorrectionRequest request) {
        if (request == null || (!request.hasBuilding() && !request.hasApartmentCode() && !request.hasFloorNo())
                || request.reason() != null && request.reason().isBlank()) {
            throw new ApartmentInvalidRequestException();
        }
        if (request.hasBuilding() && !validIdentityField(request.building(), 100)) {
            throw new ApartmentInvalidRequestException();
        }
        if (request.hasApartmentCode() && !validIdentityField(request.apartmentCode(), 50)) {
            throw new ApartmentInvalidRequestException();
        }
    }

    private boolean validIdentityField(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApartmentInvalidRequestException();
        }
    }

    private byte[] filterKey(String value, boolean building) {
        return value == null ? null : normalizedFilterKey(value, building);
    }

    private byte[] normalizedFilterKey(String value, boolean building) {
        if (!validIdentityField(value, building ? 100 : 50)) {
            throw new ApartmentInvalidRequestException();
        }
        try {
            return building
                    ? ResidentIdentityKeyNormalizer.buildingKey(value)
                    : ResidentIdentityKeyNormalizer.apartmentCodeKey(value);
        } catch (IllegalArgumentException exception) {
            throw new ApartmentInvalidRequestException();
        }
    }

    private Apartment requireApartment(Long id) {
        if (id == null || id <= 0) {
            throw new ApartmentInvalidRequestException();
        }
        return apartmentRepository.findById(id).orElseThrow(ApartmentNotFoundException::new);
    }

    private ApartmentSummary summary(Apartment apartment) {
        return new ApartmentSummary(apartment.getId(), apartment.getBuilding(), apartment.getApartmentCode(),
                apartment.getFloorNo(), apartment.getStatus());
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

    private boolean isIdentityConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && IDENTITY_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }

    private ApartmentDetail detail(Apartment apartment) {
        return new ApartmentDetail(
                apartment.getId(), apartment.getBuilding(), apartment.getApartmentCode(), apartment.getFloorNo(),
                apartment.getStatus(), apartment.getCreatedAt(), apartment.getUpdatedAt());
    }

    private String auditData(Apartment apartment, String reason) {
        try {
            return objectMapper.writeValueAsString(new ApartmentAuditData(
                    apartment.getBuilding(), apartment.getApartmentCode(), apartment.getFloorNo(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize apartment audit data", exception);
        }
    }

    private record ApartmentAuditData(
            String building,
            @com.fasterxml.jackson.annotation.JsonProperty("apartment_code") String apartmentCode,
            @com.fasterxml.jackson.annotation.JsonProperty("floor_no") Integer floorNo,
            String reason) {}
}
