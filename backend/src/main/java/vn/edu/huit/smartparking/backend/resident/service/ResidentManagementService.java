package vn.edu.huit.smartparking.backend.resident.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.repository.AuditLogRepository;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentSummary;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class ResidentManagementService {
    private static final String INSERT_OR_REUSE_RESIDENT = "INSERT INTO residents "
            + "(full_name, identity_number, identity_number_key, date_of_birth, phone, email, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?) "
            + "ON DUPLICATE KEY UPDATE id = id + LAST_INSERT_ID(0)";
    private static final String UPDATE_RESIDENT_PROFILE = "UPDATE residents "
            + "SET full_name = ?, identity_number = ?, identity_number_key = ?, date_of_birth = ?, "
            + "phone = ?, email = ?, updated_at = ? WHERE id = ?";

    private final ResidentRepository residentRepository;
    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ResidentManagementService(
            ResidentRepository residentRepository,
            AuditLogRepository auditLogRepository,
            JdbcTemplate jdbcTemplate,
            AuditService auditService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.residentRepository = residentRepository;
        this.auditLogRepository = auditLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ResidentCreateResult createOrReuse(ResidentCreateRequest request, User actor) {
        validateCreate(request);
        byte[] identityNumberKey = identityKey(request.identityNumber());
        return transactionTemplate.execute(status -> createOrReuseInTransaction(request, identityNumberKey, actor));
    }

    private ResidentCreateResult createOrReuseInTransaction(
            ResidentCreateRequest request, byte[] identityNumberKey, User actor) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(INSERT_OR_REUSE_RESIDENT,
                request.fullName(), request.identityNumber(), identityNumberKey,
                request.dateOfBirth() == null ? null : Date.valueOf(request.dateOfBirth()),
                request.phone(), request.email(), Timestamp.valueOf(now), Timestamp.valueOf(now));
        Long insertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Resident resident = residentRepository.findByIdentityNumberKeyForUpdate(identityNumberKey)
                .orElseThrow(ResidentConcurrentModificationException::new);
        boolean created = insertedId != null && insertedId > 0;
        if (created) {
            auditService.record("RESIDENT_CREATED", "RESIDENT", resident.getId().toString(), actor,
                    null, auditData("CREATED"));
        } else {
            recordReuse(resident, actor);
        }
        return new ResidentCreateResult(detail(resident), created);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ResidentSummary> list(String fullName, int page, int size) {
        validatePagination(page, size);
        if (fullName != null && fullName.length() > 150) {
            throw new ResidentInvalidRequestException();
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<Resident> residents = fullName == null || fullName.isBlank()
                ? residentRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : residentRepository.findAllByFullNameContainingOrderByCreatedAtDescIdDesc(fullName, pageable);
        List<ResidentSummary> items = residents.getContent().stream().map(this::summary).toList();
        return new PagedResponse<>(items, residents.getNumber(), residents.getSize(), residents.getTotalElements());
    }

    @Transactional
    public ResidentDetail detail(Long id, User actor) {
        Resident resident = requireResident(id);
        auditService.record("RESIDENT_DETAIL_READ", "RESIDENT", resident.getId().toString(), actor, null, null);
        return detail(resident);
    }

    @Transactional(noRollbackFor = ResidentNotFoundException.class)
    public ResidentDetail lookupByIdentityNumber(ResidentLookupRequest request, User actor) {
        if (request == null || request.identityNumber() == null || request.identityNumber().length() > 30) {
            throw new ResidentInvalidRequestException();
        }
        byte[] identityNumberKey = identityKey(request.identityNumber());
        Resident resident = residentRepository.findByIdentityNumberKey(identityNumberKey).orElse(null);
        auditService.record("RESIDENT_IDENTITY_LOOKUP", "RESIDENT",
                resident == null ? null : resident.getId().toString(), actor, null,
                auditData(resident == null ? "NOT_FOUND" : "FOUND"));
        if (resident == null) {
            throw new ResidentNotFoundException();
        }
        return detail(resident);
    }

    @Transactional(readOnly = true)
    public PagedResponse<HistoryItem> history(Long id, int page, int size) {
        validatePagination(page, size);
        Resident resident = requireResident(id);
        Page<vn.edu.huit.smartparking.backend.audit.entity.AuditLog> logs = auditLogRepository
                .findAllByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                        "RESIDENT", resident.getId().toString(), PageRequest.of(page, size));
        List<HistoryItem> items = logs.getContent().stream().map(this::historyItem).toList();
        return new PagedResponse<>(items, logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    @Transactional
    public ResidentDetail correct(Long id, ResidentCorrectionRequest request, User actor) {
        validateCorrection(request);
        if (id == null || id <= 0) {
            throw new ResidentInvalidRequestException();
        }
        Resident resident = residentRepository.findByIdForUpdate(id)
                .orElseThrow(ResidentNotFoundException::new);
        String fullName = request.hasFullName() ? request.fullName() : resident.getFullName();
        String identityNumber = request.hasIdentityNumber() ? request.identityNumber() : resident.getIdentityNumber();
        LocalDate dateOfBirth = request.hasDateOfBirth() ? request.dateOfBirth() : resident.getDateOfBirth();
        String phone = request.hasPhone() ? request.phone() : resident.getPhone();
        String email = request.hasEmail() ? request.email() : resident.getEmail();
        byte[] identityNumberKey = identityKey(identityNumber);
        residentRepository.findByIdentityNumberKey(identityNumberKey)
                .filter(existing -> !id.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new ResidentIdentityConflictException();
                });

        List<String> changedFields = changedFields(resident, fullName, identityNumber, dateOfBirth, phone, email);
        LocalDateTime updatedAt = LocalDateTime.now();
        try {
            int rows = jdbcTemplate.update(UPDATE_RESIDENT_PROFILE,
                    fullName, identityNumber, identityNumberKey,
                    dateOfBirth == null ? null : Date.valueOf(dateOfBirth), phone, email,
                    Timestamp.valueOf(updatedAt), id);
            if (rows != 1) {
                throw new ResidentConcurrentModificationException();
            }
        } catch (DuplicateKeyException exception) {
            throw new ResidentIdentityConflictException();
        }
        auditService.record("RESIDENT_CORRECTED", "RESIDENT", id.toString(), actor, null,
                auditData(changedFields, request.reason()));
        return new ResidentDetail(id, fullName, identityNumber, dateOfBirth, phone, email,
                resident.getStatus(), resident.getCreatedAt(), updatedAt);
    }

    private void recordReuse(Resident resident, User actor) {
        auditService.record("RESIDENT_REUSED", "RESIDENT", resident.getId().toString(), actor,
                null, auditData("REUSED"));
    }

    private void validateCreate(ResidentCreateRequest request) {
        if (request == null || request.fullName() == null || request.fullName().isBlank()
                || request.fullName().length() > 150 || request.identityNumber() == null
                || request.identityNumber().length() > 30
                || request.phone() != null && request.phone().length() > 30
                || request.email() != null && request.email().length() > 150) {
            throw new ResidentInvalidRequestException();
        }
        identityKey(request.identityNumber());
    }

    private byte[] identityKey(String identityNumber) {
        try {
            return ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber);
        } catch (IllegalArgumentException exception) {
            throw new ResidentInvalidRequestException();
        }
    }

    private String auditData(String outcome) {
        try {
            return objectMapper.writeValueAsString(Map.of("outcome", outcome));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize Resident audit data", exception);
        }
    }

    private String auditData(List<String> changedFields, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changed_fields", changedFields);
        data.put("reason", reason);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize Resident audit data", exception);
        }
    }

    private void validateCorrection(ResidentCorrectionRequest request) {
        if (request == null || (!request.hasFullName() && !request.hasIdentityNumber()
                && !request.hasDateOfBirth() && !request.hasPhone() && !request.hasEmail())
                || request.reason() != null && request.reason().isBlank()) {
            throw new ResidentInvalidRequestException();
        }
        if (request.hasFullName() && !validValue(request.fullName(), 150)) {
            throw new ResidentInvalidRequestException();
        }
        if (request.hasIdentityNumber() && !validValue(request.identityNumber(), 30)) {
            throw new ResidentInvalidRequestException();
        }
        if (request.hasPhone() && request.phone() != null && request.phone().length() > 30) {
            throw new ResidentInvalidRequestException();
        }
        if (request.hasEmail() && request.email() != null && request.email().length() > 150) {
            throw new ResidentInvalidRequestException();
        }
        if (request.hasIdentityNumber() && (request.reason() == null || request.reason().isBlank())) {
            throw new ResidentInvalidRequestException();
        }
    }

    private boolean validValue(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResidentInvalidRequestException();
        }
    }

    private Resident requireResident(Long id) {
        if (id == null || id <= 0) {
            throw new ResidentInvalidRequestException();
        }
        return residentRepository.findById(id).orElseThrow(ResidentNotFoundException::new);
    }

    private ResidentSummary summary(Resident resident) {
        return new ResidentSummary(resident.getId(), resident.getFullName(), resident.getStatus());
    }

    private HistoryItem historyItem(vn.edu.huit.smartparking.backend.audit.entity.AuditLog log) {
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

    private List<String> changedFields(
            Resident resident, String fullName, String identityNumber, LocalDate dateOfBirth, String phone, String email) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(resident.getFullName(), fullName)) {
            fields.add("full_name");
        }
        if (!Objects.equals(resident.getIdentityNumber(), identityNumber)) {
            fields.add("identity_number");
        }
        if (!Objects.equals(resident.getDateOfBirth(), dateOfBirth)) {
            fields.add("date_of_birth");
        }
        if (!Objects.equals(resident.getPhone(), phone)) {
            fields.add("phone");
        }
        if (!Objects.equals(resident.getEmail(), email)) {
            fields.add("email");
        }
        return fields;
    }

    private ResidentDetail detail(Resident resident) {
        return new ResidentDetail(resident.getId(), resident.getFullName(), resident.getIdentityNumber(),
                resident.getDateOfBirth(), resident.getPhone(), resident.getEmail(), resident.getStatus(),
                resident.getCreatedAt(), resident.getUpdatedAt());
    }
}
