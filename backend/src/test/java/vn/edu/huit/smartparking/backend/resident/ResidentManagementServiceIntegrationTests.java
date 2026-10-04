package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.resident.service.ResidentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentCreateResult;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;

@SpringBootTest
@ActiveProfiles("test")
class ResidentManagementServiceIntegrationTests {
    @Autowired
    private ResidentManagementService residentService;

    @Autowired
    private ResidentStatusManagementService residentStatusService;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsActiveResidentAndReusesEquivalentIdentityWithoutDuplicatingProfile() {
        String identityNumber = "AHR04-" + UUID.randomUUID().toString().substring(0, 12);

        ResidentCreateResult created = residentService.createOrReuse(
                new ResidentCreateRequest("Synthetic Resident", identityNumber, null, null, null), null);
        ResidentCreateResult reused = residentService.createOrReuse(
                new ResidentCreateRequest("Different Submitted Name", "\t" + identityNumber.toLowerCase(Locale.ROOT) + " ",
                        null, null, null), null);

        assertTrue(created.created());
        assertFalse(reused.created());
        assertNotNull(created.resident().id());
        assertEquals(ResidentStatus.ACTIVE, created.resident().status());
        assertEquals("Synthetic Resident", reused.resident().fullName());
        assertEquals(created.resident().id(), reused.resident().id());
        assertEquals(created.resident().id(), residentRepository
                .findByCanonicalIdentityNumber(identityNumber).orElseThrow().getId());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residents WHERE identity_number_key = ?",
                Integer.class, ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                        + "AND action = 'RESIDENT_CREATED'", Integer.class, created.resident().id().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                        + "AND action = 'RESIDENT_REUSED'", Integer.class, created.resident().id().toString()));
        String auditData = jdbcTemplate.queryForObject(
                "SELECT CONCAT(COALESCE(old_data, ''), ' ', COALESCE(new_data, '')) "
                        + "FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                        + "AND action = 'RESIDENT_REUSED'", String.class, created.resident().id().toString());
        assertFalse(auditData.contains(identityNumber));
    }

    @Test
    void residentStatusFollowsTheApprovedTransitionMatrixAndAuditsEachChange() {
        String identityNumber = "AHR10-STATUS-" + UUID.randomUUID().toString().substring(0, 8);
        Long residentId = residentService.createOrReuse(
                new ResidentCreateRequest("Status Matrix Resident", identityNumber, null, null, null), null)
                .resident().id();

        try {
            assertEquals(ResidentStatus.BLOCKED, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Block", null, null), null).status());
            assertEquals(ResidentStatus.ACTIVE, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.ACTIVE, "Unblock", null, null), null).status());
            assertEquals(ResidentStatus.INACTIVE, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Deactivate", null, null), null).status());
            assertThrows(ResidentStatusConflictException.class, () -> residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Invalid transition", null, null), null));
            assertEquals(ResidentStatus.ACTIVE, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.ACTIVE, "Reactivate", null, null), null).status());
            assertEquals(ResidentStatus.BLOCKED, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Block again", null, null), null).status());
            assertEquals(ResidentStatus.INACTIVE, residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Deactivate blocked resident", null, null), null)
                    .status());
            assertEquals(6, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                            + "AND action = 'RESIDENT_STATUS_CHANGED'",
                    Integer.class, residentId.toString()));
        } finally {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ?",
                    residentId.toString());
            residentRepository.deleteById(residentId);
        }
    }

    @Test
    @Transactional
    void residentListSearchesPartialFullNameAndReturnsMinimizedPagedSummaries() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        residentService.createOrReuse(new ResidentCreateRequest(
                "AHR04 Resident " + token + " First", "LIST1-" + token, null, null, null), null);
        residentService.createOrReuse(new ResidentCreateRequest(
                "AHR04 Resident " + token + " Second", "LIST2-" + token, null, null, null), null);

        PagedResponse<ResidentSummary> firstPage = residentService.list("Resident " + token, 0, 1);
        PagedResponse<ResidentSummary> secondPage = residentService.list("Resident " + token, 1, 1);

        assertEquals(2, firstPage.totalItems());
        assertEquals(1, firstPage.items().size());
        assertEquals(1, secondPage.items().size());
        assertTrue(firstPage.items().getFirst().fullName().contains(token));
        assertTrue(firstPage.items().getFirst().id() != secondPage.items().getFirst().id());
    }

    @Test
    @Transactional
    void residentDetailAndExactLookupAuditEachOperationOnceWithoutIdentityInAudit() {
        String identityNumber = "AHR04-READ-" + UUID.randomUUID().toString().substring(0, 8);
        ResidentCreateResult created = residentService.createOrReuse(
                new ResidentCreateRequest("Read Audit Resident", identityNumber, null, null, null), null);

        ResidentDetail detail = residentService.detail(created.resident().id(), null);
        ResidentDetail found = residentService.lookupByIdentityNumber(
                new ResidentLookupRequest("\t" + identityNumber.toLowerCase(Locale.ROOT) + " "), null);

        assertEquals(identityNumber, detail.identityNumber());
        assertEquals(created.resident().id(), found.id());
        assertEquals(1, auditCount(created.resident().id(), "RESIDENT_DETAIL_READ"));
        assertEquals(1, auditCount(created.resident().id(), "RESIDENT_IDENTITY_LOOKUP"));
        String lookupData = jdbcTemplate.queryForObject(
                "SELECT new_data FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                        + "AND action = 'RESIDENT_IDENTITY_LOOKUP'", String.class, created.resident().id().toString());
        assertFalse(lookupData.contains(identityNumber));
    }

    @Test
    @Transactional
    void missingExactIdentityLookupIsAuditedOnceBeforeReturningNotFound() {
        String identityNumber = "AHR04-MISS-" + UUID.randomUUID().toString().substring(0, 8);

        assertThrows(ResidentNotFoundException.class,
                () -> residentService.lookupByIdentityNumber(new ResidentLookupRequest(identityNumber), null));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'RESIDENT_IDENTITY_LOOKUP' "
                        + "AND entity_type = 'RESIDENT' AND entity_id IS NULL", Integer.class));
        String auditData = jdbcTemplate.queryForObject(
                "SELECT new_data FROM audit_logs WHERE action = 'RESIDENT_IDENTITY_LOOKUP' "
                        + "AND entity_type = 'RESIDENT' AND entity_id IS NULL", String.class);
        assertFalse(auditData.contains(identityNumber));
    }

    @Test
    @Transactional
    void correctionRequiresIdentityReasonClearsOptionalFieldsAndAuditsOnlySafeMetadata() {
        String oldIdentity = "AHR04-OLD-" + UUID.randomUUID().toString().substring(0, 8);
        String newIdentity = "AHR04-NEW-" + UUID.randomUUID().toString().substring(0, 8);
        ResidentCreateResult created = residentService.createOrReuse(new ResidentCreateRequest(
                "Before Correction", oldIdentity, LocalDate.of(1988, 4, 3), "555-0100", "resident@example.invalid"),
                null);
        ResidentCorrectionRequest correction = new ResidentCorrectionRequest();
        correction.setFullName("After Correction");
        correction.setIdentityNumber(newIdentity);
        correction.setDateOfBirth(null);
        correction.setPhone(null);
        correction.setEmail(null);

        assertThrows(ResidentInvalidRequestException.class,
                () -> residentService.correct(created.resident().id(), correction, null));

        correction.setReason("Verified identity correction");
        ResidentDetail corrected = residentService.correct(created.resident().id(), correction, null);

        assertEquals("After Correction", corrected.fullName());
        assertEquals(newIdentity, corrected.identityNumber());
        assertEquals(null, corrected.dateOfBirth());
        assertEquals(null, corrected.phone());
        assertEquals(null, corrected.email());
        assertEquals(ResidentStatus.ACTIVE, corrected.status());
        assertEquals(created.resident().id(), residentRepository.findByCanonicalIdentityNumber(newIdentity)
                .orElseThrow().getId());
        assertEquals("Verified identity correction",
                residentService.history(corrected.id(), 0, 20).items().getFirst().reason());
        String correctionAudit = jdbcTemplate.queryForObject(
                "SELECT CONCAT(COALESCE(old_data, ''), ' ', COALESCE(new_data, '')) FROM audit_logs "
                        + "WHERE entity_type = 'RESIDENT' AND entity_id = ? AND action = 'RESIDENT_CORRECTED'",
                String.class, corrected.id().toString());
        assertFalse(correctionAudit.contains(oldIdentity));
        assertFalse(correctionAudit.contains(newIdentity));
    }

    @Test
    @Transactional
    void correctionRejectsIdentityAlreadyAssignedToAnotherResident() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        ResidentCreateResult first = residentService.createOrReuse(
                new ResidentCreateRequest("First Resident", "AHR04-FIRST-" + token, null, null, null), null);
        ResidentCreateResult second = residentService.createOrReuse(
                new ResidentCreateRequest("Second Resident", "AHR04-SECOND-" + token, null, null, null), null);
        ResidentCorrectionRequest correction = new ResidentCorrectionRequest();
        correction.setIdentityNumber(first.resident().identityNumber());
        correction.setReason("Verified correction");

        assertThrows(ResidentIdentityConflictException.class,
                () -> residentService.correct(second.resident().id(), correction, null));
    }

    @Test
    void concurrentEquivalentCreatesReuseTheSingleCommittedResident() throws Exception {
        String identityNumber = "AHR04-" + UUID.randomUUID().toString().substring(0, 12);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ResidentCreateResult>> results = List.of(
                executor.submit(() -> concurrentCreate("First Name", identityNumber, ready, start)),
                executor.submit(() -> concurrentCreate(
                        "Second Name", " \t" + identityNumber.toLowerCase(Locale.ROOT) + " ", ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            ResidentCreateResult first = results.get(0).get(10, TimeUnit.SECONDS);
            ResidentCreateResult second = results.get(1).get(10, TimeUnit.SECONDS);

            assertTrue(first.created() ^ second.created());
            assertEquals(first.resident().id(), second.resident().id());
            Long residentId = first.resident().id();
            byte[] identityKey = ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM residents WHERE identity_number_key = ?", Integer.class, identityKey));
            assertEquals(1, auditCount(residentId, "RESIDENT_CREATED"));
            assertEquals(1, auditCount(residentId, "RESIDENT_REUSED"));
            String reuseData = jdbcTemplate.queryForObject(
                    "SELECT new_data FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                            + "AND action = 'RESIDENT_REUSED'", String.class, residentId.toString());
            assertFalse(reuseData.contains(identityNumber));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            residentRepository.findByCanonicalIdentityNumber(identityNumber).ifPresent(resident -> {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ?",
                        resident.getId().toString());
                residentRepository.deleteById(resident.getId());
            });
        }
    }

    @Test
    @Transactional
    void mysqlResidentIdentityUpsertDistinguishesInsertFromReuseWithoutDuplicateErrors() {
        String identityNumber = "AHR04-U" + UUID.randomUUID().toString().substring(0, 10);
        byte[] identityKey = ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber);
        String sql = "INSERT INTO residents (full_name, identity_number, identity_number_key, status) "
                + "VALUES (?, ?, ?, 'ACTIVE') ON DUPLICATE KEY UPDATE id = id + LAST_INSERT_ID(0)";

        jdbcTemplate.update(sql, "Upsert Fixture", identityNumber, identityKey);
        Long insertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(sql, "Ignored Replacement Name", identityNumber, identityKey);
        Long reusedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        assertTrue(insertedId != null && insertedId > 0);
        assertEquals(0L, reusedId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residents WHERE identity_number_key = ?", Integer.class, identityKey));
    }

    private ResidentCreateResult concurrentCreate(
            String fullName, String identityNumber, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent Resident test did not start");
        }
        return residentService.createOrReuse(
                new ResidentCreateRequest(fullName, identityNumber, null, null, null), null);
    }

    private int auditCount(Long residentId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? AND action = ?",
                Integer.class, residentId.toString(), action);
    }
}
