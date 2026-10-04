package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipLifecycleRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipTransferRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipVoidRequest;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.HouseholdHeadConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipOverlapException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.security.entity.User;

@SpringBootTest
@ActiveProfiles("test")
class ApartmentMembershipManagementServiceIntegrationTests {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ApartmentManagementService apartmentService;

    @Autowired
    private ResidentManagementService residentService;

    @Autowired
    private ApartmentMembershipManagementService membershipService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditService auditService;

    @Test
    @Transactional
    void addsScheduledMemberAndExposesOnlyMembershipFieldsWithSanitizedHistory() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Building " + token, "A-01", null), null).id();
        Long residentId = residentService.createOrReuse(new ResidentCreateRequest(
                "AHR05 Resident " + token, "AHR05-" + token, null, null, null), null).resident().id();
        LocalDateTime validFrom = LocalDateTime.of(2027, 1, 1, 0, 0);
        LocalDateTime validTo = LocalDateTime.of(2027, 7, 1, 0, 0);

        MembershipDetail created = membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, validFrom, validTo, "Verified household record"), null);
        PagedResponse<MembershipDetail> page = membershipService.list(apartmentId, residentId, 0, 20);

        assertNotNull(created.id());
        assertEquals(apartmentId, created.apartmentId());
        assertEquals(residentId, created.residentId());
        assertEquals(MembershipRole.MEMBER, created.memberRole());
        assertEquals(validFrom, created.validFrom());
        assertEquals(validTo, created.validTo());
        assertEquals(MembershipStatus.ACTIVE, created.status());
        assertNull(created.lifecycleChangedAt());
        assertNull(created.lifecycleReason());
        assertEquals(1, page.totalItems());
        assertEquals(created.id(), page.items().getFirst().id());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                        + "AND entity_id = ? AND action = 'MEMBERSHIP_CREATED'",
                Integer.class, created.id().toString()));
        assertEquals("Verified household record", membershipService.history(created.id(), 0, 20)
                .items().getFirst().reason());
    }

    @Test
    @Transactional
    void membershipDetailReadIsAuditedWhileListAndHistoryReadsAreNot() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Read Building " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR05 Read Resident " + token, "AHR05-READ-" + token);
        MembershipDetail created = membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, LocalDateTime.of(2027, 1, 1, 0, 0), null,
                "Read audit fixture"), null);
        int beforeRead = membershipAuditCount(created.id());

        membershipService.list(apartmentId, null, 0, 20);
        membershipService.history(created.id(), 0, 20);
        assertEquals(beforeRead, membershipAuditCount(created.id()));

        membershipService.detail(created.id(), null);

        assertEquals(beforeRead + 1, membershipAuditCount(created.id()));
        assertEquals("MEMBERSHIP_DETAIL_READ", membershipService.history(created.id(), 0, 20)
                .items().getFirst().action());
    }

    @Test
    @Transactional
    void permitsAdjacentMembershipIntervalsAndRejectsOverlappingIntervalsForTheSameResident() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Boundary Building " + token, "A-01", null), null).id();
        Long residentId = residentService.createOrReuse(new ResidentCreateRequest(
                "AHR05 Boundary Resident " + token, "AHR05-B-" + token, null, null, null), null).resident().id();
        LocalDateTime start = LocalDateTime.of(2027, 1, 1, 0, 0);
        LocalDateTime boundary = LocalDateTime.of(2027, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2028, 1, 1, 0, 0);

        membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, start, boundary, "First period"), null);
        membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, boundary, end, "Adjacent period"), null);

        assertThrows(MembershipOverlapException.class, () -> membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, boundary.minusDays(1), end, "Overlapping period"), null));
    }

    @Test
    @Transactional
    void rejectsMembershipCreationForInactiveApartmentOrResident() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long activeApartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Status Active Building " + token, "A-01", null), null).id();
        Long inactiveApartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Status Inactive Building " + token, "A-01", null), null).id();
        Long activeResidentId = createResident("AHR05 Status Active " + token, "AHR05-SA-" + token);
        Long inactiveResidentId = createResident("AHR05 Status Inactive " + token, "AHR05-SI-" + token);
        entityManager.find(Apartment.class, inactiveApartmentId).setStatus(ApartmentStatus.INACTIVE);
        entityManager.find(Resident.class, inactiveResidentId).setStatus(ResidentStatus.INACTIVE);
        entityManager.flush();
        LocalDateTime validFrom = LocalDateTime.of(2027, 1, 1, 0, 0);

        assertThrows(MembershipStatusConflictException.class, () -> membershipService.add(new MembershipCreateRequest(
                activeApartmentId, inactiveResidentId, MembershipRole.MEMBER, validFrom, null, "Inactive resident"), null));
        assertThrows(MembershipStatusConflictException.class, () -> membershipService.add(new MembershipCreateRequest(
                inactiveApartmentId, activeResidentId, MembershipRole.MEMBER, validFrom, null, "Inactive apartment"), null));
    }

    @Test
    @Transactional
    void assignsOnlyOneOverlappingHouseholdHeadButAllowsMultipleMembers() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Head Building " + token, "A-01", null), null).id();
        Long firstResidentId = createResident("AHR05 Head One " + token, "AHR05-H1-" + token);
        Long secondResidentId = createResident("AHR05 Head Two " + token, "AHR05-H2-" + token);
        LocalDateTime validFrom = LocalDateTime.of(2027, 1, 1, 0, 0);
        LocalDateTime validTo = LocalDateTime.of(2028, 1, 1, 0, 0);

        MembershipDetail head = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(firstResidentId, validFrom, validTo, "Verified household head"), null);

        assertEquals(MembershipRole.HOUSEHOLD_HEAD, head.memberRole());
        assertThrows(HouseholdHeadConflictException.class, () -> membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(secondResidentId, validFrom.plusMonths(1), validTo,
                        "Conflicting head assignment"), null));
        assertEquals(MembershipRole.MEMBER, membershipService.add(new MembershipCreateRequest(
                apartmentId, secondResidentId, MembershipRole.MEMBER, validFrom, validTo, "Verified household member"), null)
                .memberRole());
    }

    @Test
    @Transactional
    void endAndRevokeKeepEffectiveTimeSeparateFromLifecycleTimeAndPreserveHistory() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Lifecycle Building " + token, "A-01", null), null).id();
        Long endedResidentId = createResident("AHR05 End Resident " + token, "AHR05-E-" + token);
        Long revokedResidentId = createResident("AHR05 Revoke Resident " + token, "AHR05-R-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(10).withNano(0);
        LocalDateTime validTo = LocalDateTime.now().plusDays(10).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(2).withNano(0);
        MembershipDetail toEnd = membershipService.add(new MembershipCreateRequest(
                apartmentId, endedResidentId, MembershipRole.MEMBER, validFrom, validTo, "Initial membership"), null);
        MembershipDetail toRevoke = membershipService.add(new MembershipCreateRequest(
                apartmentId, revokedResidentId, MembershipRole.MEMBER, validFrom, validTo, "Second membership"), null);

        MembershipDetail ended = membershipService.end(toEnd.id(),
                new MembershipLifecycleRequest(effectiveAt, "Membership ended"), null);
        MembershipDetail revoked = membershipService.revoke(toRevoke.id(),
                new MembershipLifecycleRequest(effectiveAt, "Membership revoked"), null);

        assertEquals(MembershipStatus.INACTIVE, ended.status());
        assertEquals(effectiveAt, ended.validTo());
        assertNotNull(ended.lifecycleChangedAt());
        assertEquals("Membership ended", ended.lifecycleReason());
        assertEquals(MembershipStatus.REVOKED, revoked.status());
        assertEquals(effectiveAt, revoked.validTo());
        assertNotNull(revoked.lifecycleChangedAt());
        assertEquals("Membership revoked", revoked.lifecycleReason());
        assertEquals("Membership ended", membershipService.history(toEnd.id(), 0, 20).items()
                .getFirst().reason());
        assertThrows(MembershipStateConflictException.class, () -> membershipService.end(toEnd.id(),
                new MembershipLifecycleRequest(effectiveAt, "Repeated end"), null));
    }

    @Test
    @Transactional
    void voidMembershipPreservesTheOriginalIntervalAndAuditsCreatedInError() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR11 Void Building " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR11 Void Resident " + token, "AHR11-V-" + token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime validTo = validFrom.plusDays(30);
        MembershipDetail created = membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, validFrom, validTo, "Membership fixture"), null);

        MembershipDetail voided = membershipService.voidMembership(
                created.id(), new MembershipVoidRequest("Created in error"), null);

        assertEquals(MembershipStatus.VOID, voided.status());
        assertEquals(validFrom, voided.validFrom());
        assertEquals(validTo, voided.validTo());
        assertNotNull(voided.lifecycleChangedAt());
        assertEquals("Created in error", voided.lifecycleReason());
        assertEquals("MEMBERSHIP_VOIDED", membershipService.history(created.id(), 0, 10)
                .items().getFirst().action());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                        + "AND entity_id = ? AND action = 'MEMBERSHIP_VOIDED'",
                Integer.class, created.id().toString()));
        assertThrows(MembershipStateConflictException.class, () -> membershipService.voidMembership(
                created.id(), new MembershipVoidRequest("Repeated VOID"), null));
    }

    @Test
    void rejectsFutureEffectiveEndWithoutChangingScheduledMembership() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Future End " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR05 Future End Resident " + token, "AHR05-FE-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime validTo = LocalDateTime.now().plusDays(10).withNano(0);
        MembershipDetail membership = membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, validFrom, validTo, "Future end fixture"), null);

        try {
            assertThrows(MembershipStateConflictException.class, () -> membershipService.end(membership.id(),
                    new MembershipLifecycleRequest(LocalDateTime.now().plusDays(1), "Future end"), null));
            assertEquals(MembershipStatus.ACTIVE, membershipService.detail(membership.id(), null).status());
        } finally {
            cleanupFixtures(apartmentId, residentId);
        }
    }

    @Test
    void voidMembershipAcceptsActiveEndedAndRevokedRowsWithoutRewritingTheirIntervals() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR11 VOID states " + token, "A-01", null), null).id();
        Long activeResidentId = createResident("AHR11 VOID active " + token, "AHR11-VSA-" + token);
        Long endedResidentId = createResident("AHR11 VOID ended " + token, "AHR11-VSE-" + token);
        Long revokedResidentId = createResident("AHR11 VOID revoked " + token, "AHR11-VSR-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(20).withNano(0);
        LocalDateTime validTo = LocalDateTime.now().plusDays(20).withNano(0);
        MembershipDetail active = membershipService.add(new MembershipCreateRequest(
                apartmentId, activeResidentId, MembershipRole.MEMBER, validFrom, null, "Active fixture"), null);
        MembershipDetail endedSource = membershipService.add(new MembershipCreateRequest(
                apartmentId, endedResidentId, MembershipRole.MEMBER, validFrom, validTo, "Ended fixture"), null);
        MembershipDetail revokedSource = membershipService.add(new MembershipCreateRequest(
                apartmentId, revokedResidentId, MembershipRole.MEMBER, validFrom, validTo, "Revoked fixture"), null);
        LocalDateTime endedAt = LocalDateTime.now().minusDays(5).withNano(0);
        LocalDateTime revokedAt = LocalDateTime.now().minusDays(4).withNano(0);
        membershipService.end(endedSource.id(),
                new MembershipLifecycleRequest(endedAt, "End before VOID"), null);
        membershipService.revoke(revokedSource.id(),
                new MembershipLifecycleRequest(revokedAt, "Revoke before VOID"), null);

        try {
            MembershipDetail voidedActive = membershipService.voidMembership(
                    active.id(), new MembershipVoidRequest("Active created in error"), null);
            MembershipDetail voidedEnded = membershipService.voidMembership(
                    endedSource.id(), new MembershipVoidRequest("Ended created in error"), null);
            MembershipDetail voidedRevoked = membershipService.voidMembership(
                    revokedSource.id(), new MembershipVoidRequest("Revoked created in error"), null);

            assertEquals(MembershipStatus.VOID, voidedActive.status());
            assertEquals(validFrom, voidedActive.validFrom());
            assertNull(voidedActive.validTo());
            assertEquals(MembershipStatus.VOID, voidedEnded.status());
            assertEquals(endedAt, voidedEnded.validTo());
            assertEquals(MembershipStatus.VOID, voidedRevoked.status());
            assertEquals(revokedAt, voidedRevoked.validTo());
            for (MembershipDetail voided : List.of(voidedActive, voidedEnded, voidedRevoked)) {
                assertNotNull(voided.lifecycleChangedAt());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                                + "AND entity_id = ? AND action = 'MEMBERSHIP_VOIDED'",
                        Integer.class, voided.id().toString()));
            }
        } finally {
            cleanupFixtures(apartmentId, activeResidentId, endedResidentId, revokedResidentId);
        }
    }

    @Test
    @Transactional
    void householdHeadTransferSchedulesOldAndNewIntervalsAtomically() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Transfer Building " + token, "A-01", null), null).id();
        Long previousHeadId = createResident("AHR06 Previous Head " + token, "AHR06-PH-" + token);
        Long nextHeadId = createResident("AHR06 Next Head " + token, "AHR06-NH-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().plusDays(1).withNano(0);
        MembershipDetail oldMembership = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(previousHeadId, validFrom, null, "Initial head"), null);

        MembershipDetail transferred = membershipService.transferHouseholdHead(apartmentId,
                new MembershipTransferRequest(oldMembership.id(), nextHeadId, effectiveAt,
                        "Household head changed"), null);

        MembershipDetail ended = membershipService.detail(oldMembership.id(), null);
        assertEquals(previousHeadId, ended.residentId());
        assertEquals(MembershipRole.HOUSEHOLD_HEAD, ended.memberRole());
        assertEquals(MembershipStatus.ACTIVE, ended.status());
        assertEquals(effectiveAt, ended.validTo());
        assertNull(ended.lifecycleChangedAt());
        assertNull(ended.lifecycleReason());
        assertEquals(apartmentId, transferred.apartmentId());
        assertEquals(nextHeadId, transferred.residentId());
        assertEquals(MembershipRole.HOUSEHOLD_HEAD, transferred.memberRole());
        assertEquals(MembershipStatus.ACTIVE, transferred.status());
        assertEquals(effectiveAt, transferred.validFrom());
        assertNull(transferred.validTo());
        assertTrue(membershipService.history(oldMembership.id(), 0, 20).items().stream()
                .anyMatch(item -> "HOUSEHOLD_HEAD_TRANSFERRED_OUT".equals(item.action())
                        && "Household head changed".equals(item.reason())));
        assertTrue(membershipService.history(transferred.id(), 0, 20).items().stream()
                .anyMatch(item -> "HOUSEHOLD_HEAD_TRANSFERRED_IN".equals(item.action())));
    }

    @Test
    void householdHeadTransferAuditFailureRollsBackBothMembershipAndTransferOutAudit() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Transfer Rollback " + token, "A-01", null), null).id();
        Long previousHeadId = createResident("AHR06 Rollback Previous " + token, "AHR06-RP-" + token);
        Long nextHeadId = createResident("AHR06 Rollback Next " + token, "AHR06-RN-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().plusDays(1).withNano(0);
        MembershipDetail oldMembership = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(previousHeadId, validFrom, null, "Initial head"), null);
        doAnswer(invocation -> {
            if ("HOUSEHOLD_HEAD_TRANSFERRED_IN".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic transfer audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class, () -> membershipService.transferHouseholdHead(apartmentId,
                    new MembershipTransferRequest(oldMembership.id(), nextHeadId, effectiveAt,
                            "Household head changed"), null));

            MembershipDetail unchangedSource = membershipService.detail(oldMembership.id(), null);
            assertEquals(MembershipStatus.ACTIVE, unchangedSource.status());
            assertNull(unchangedSource.validTo());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND resident_id = ?",
                    Integer.class, apartmentId, nextHeadId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'HOUSEHOLD_HEAD_TRANSFERRED_OUT'",
                    Integer.class, oldMembership.id().toString()));
        } finally {
            cleanupFixtures(apartmentId, previousHeadId, nextHeadId);
        }
    }

    @Test
    @Transactional
    void householdHeadTransferRejectsOverlappingTargetMembershipAndPreservesSource() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Transfer Conflict " + token, "A-01", null), null).id();
        Long previousHeadId = createResident("AHR06 Conflict Previous " + token, "AHR06-CP-" + token);
        Long nextHeadId = createResident("AHR06 Conflict Next " + token, "AHR06-CN-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime nextMemberEnd = LocalDateTime.now().plusDays(30).withNano(0);
        MembershipDetail oldMembership = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(previousHeadId, validFrom, null, "Initial head"), null);
        membershipService.add(new MembershipCreateRequest(apartmentId, nextHeadId, MembershipRole.MEMBER,
                validFrom, nextMemberEnd, "Existing member period"), null);

        assertThrows(MembershipOverlapException.class, () -> membershipService.transferHouseholdHead(apartmentId,
                new MembershipTransferRequest(oldMembership.id(), nextHeadId, effectiveAt,
                        "Transfer conflicts with current membership"), null));

        assertEquals(MembershipStatus.ACTIVE, membershipService.detail(oldMembership.id(), null).status());
    }

    @Test
    void concurrentHouseholdHeadTransfersCommitOnlyOneSuccessor() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Concurrent Transfer " + token, "A-01", null), null).id();
        Long previousHeadId = createResident("AHR06 Concurrent Previous " + token, "AHR06-CTP-" + token);
        Long firstTargetId = createResident("AHR06 Concurrent Target One " + token, "AHR06-CT1-" + token);
        Long secondTargetId = createResident("AHR06 Concurrent Target Two " + token, "AHR06-CT2-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);
        MembershipDetail oldHead = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(previousHeadId, validFrom, null, "Initial head"), null);
        MembershipTransferRequest firstRequest = new MembershipTransferRequest(
                oldHead.id(), firstTargetId, effectiveAt, "Concurrent transfer");
        MembershipTransferRequest secondRequest = new MembershipTransferRequest(
                oldHead.id(), secondTargetId, effectiveAt, "Concurrent transfer");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<MembershipDetail>> results = List.of(
                executor.submit(() -> concurrentTransfer(apartmentId, firstRequest, ready, start)),
                executor.submit(() -> concurrentTransfer(apartmentId, secondRequest, ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int transferred = 0;
            int conflicts = 0;
            for (Future<MembershipDetail> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    transferred++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof MembershipStateConflictException);
                    conflicts++;
                }
            }
            assertEquals(1, transferred, () -> jdbcTemplate.queryForList(
                    "SELECT id, resident_id, member_role, status, valid_from, valid_to "
                            + "FROM apartment_memberships WHERE apartment_id = ? ORDER BY id",
                    apartmentId).toString());
            assertEquals(1, conflicts);
            LocalDateTime now = LocalDateTime.now();
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? "
                            + "AND member_role = 'HOUSEHOLD_HEAD' AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, apartmentId, now, now));
            MembershipDetail previousHead = membershipService.detail(oldHead.id(), null);
            assertEquals(MembershipStatus.ACTIVE, previousHead.status());
            assertEquals(effectiveAt, previousHead.validTo());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(apartmentId, previousHeadId, firstTargetId, secondTargetId);
        }
    }

    @Test
    void concurrentEquivalentMembershipAddsSerializeOnResidentAndApartmentLocks() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Concurrent Member " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR05 Concurrent Resident " + token, "AHR05-C-" + token);
        LocalDateTime validFrom = LocalDateTime.of(2027, 1, 1, 0, 0);
        MembershipCreateRequest request = new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, validFrom, null, "Concurrent assignment");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<MembershipDetail>> results = List.of(
                executor.submit(() -> concurrentAdd(request, ready, start)),
                executor.submit(() -> concurrentAdd(request, ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int created = 0;
            int conflicts = 0;
            for (Future<MembershipDetail> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    created++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof MembershipOverlapException);
                    conflicts++;
                }
            }
            assertEquals(1, created);
            assertEquals(1, conflicts);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND resident_id = ? "
                            + "AND status = 'ACTIVE'",
                    Integer.class, apartmentId, residentId));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(apartmentId, residentId);
        }
    }

    @Test
    void concurrentHouseholdHeadAssignmentsReturnOneCreatedRelationAndOneConflict() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR05 Concurrent Head " + token, "A-01", null), null).id();
        Long firstResidentId = createResident("AHR05 Concurrent Head One " + token, "AHR05-CH1-" + token);
        Long secondResidentId = createResident("AHR05 Concurrent Head Two " + token, "AHR05-CH2-" + token);
        LocalDateTime validFrom = LocalDateTime.of(2027, 1, 1, 0, 0);
        LocalDateTime validTo = LocalDateTime.of(2028, 1, 1, 0, 0);
        MembershipHeadAssignRequest firstRequest = new MembershipHeadAssignRequest(
                firstResidentId, validFrom, validTo, "Concurrent head assignment");
        MembershipHeadAssignRequest secondRequest = new MembershipHeadAssignRequest(
                secondResidentId, validFrom, validTo, "Concurrent head assignment");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<MembershipDetail>> results = List.of(
                executor.submit(() -> concurrentHead(apartmentId, firstRequest, ready, start)),
                executor.submit(() -> concurrentHead(apartmentId, secondRequest, ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int created = 0;
            int conflicts = 0;
            for (Future<MembershipDetail> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    created++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof HouseholdHeadConflictException);
                    conflicts++;
                }
            }
            assertEquals(1, created);
            assertEquals(1, conflicts);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? "
                            + "AND member_role = 'HOUSEHOLD_HEAD' AND status = 'ACTIVE'",
                    Integer.class, apartmentId));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(apartmentId, firstResidentId, secondResidentId);
        }
    }

    private Long createResident(String fullName, String identityNumber) {
        return residentService.createOrReuse(
                new ResidentCreateRequest(fullName, identityNumber, null, null, null), null).resident().id();
    }

    private MembershipDetail concurrentAdd(
            MembershipCreateRequest request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent membership test did not start");
        }
        return membershipService.add(request, null);
    }

    private MembershipDetail concurrentHead(
            Long apartmentId, MembershipHeadAssignRequest request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent household-head test did not start");
        }
        return membershipService.assignHouseholdHead(apartmentId, request, null);
    }

    private MembershipDetail concurrentTransfer(
            Long apartmentId, MembershipTransferRequest request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent household-head transfer test did not start");
        }
        return membershipService.transferHouseholdHead(apartmentId, request, null);
    }

    private void cleanupFixtures(Long apartmentId, Long... residentIds) {
        List<String> membershipIds = jdbcTemplate.queryForList(
                "SELECT CAST(id AS CHAR) FROM apartment_memberships WHERE apartment_id = ?", String.class, apartmentId);
        for (String membershipId : membershipIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' AND entity_id = ?",
                    membershipId);
        }
        jdbcTemplate.update("DELETE FROM apartment_memberships WHERE apartment_id = ?", apartmentId);
        for (Long residentId : residentIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ?",
                    residentId.toString());
            jdbcTemplate.update("DELETE FROM residents WHERE id = ?", residentId);
        }
        jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ?",
                apartmentId.toString());
        jdbcTemplate.update("DELETE FROM apartments WHERE id = ?", apartmentId);
    }

    private int membershipAuditCount(Long membershipId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' AND entity_id = ?",
                Integer.class, membershipId.toString());
    }
}
