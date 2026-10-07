package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

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
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentReactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipEndRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.security.entity.User;

@SpringBootTest
@ActiveProfiles("test")
class ApartmentStatusManagementServiceIntegrationTests {
    @Autowired
    private ApartmentManagementService apartmentService;

    @Autowired
    private ResidentManagementService residentService;

    @Autowired
    private ApartmentMembershipManagementService membershipService;

    @Autowired
    private ApartmentStatusManagementService apartmentStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditService auditService;

    @Test
    void deactivationRequiresAllEffectiveMembershipEndsAndReactivationDoesNotRestoreThem() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Status Building " + token, "A-01", null), null).id();
        Long memberId = createResident("AHR06 Status Member " + token, "AHR06-SM-" + token);
        Long headId = createResident("AHR06 Status Head " + token, "AHR06-SH-" + token);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime memberFrom = now.minusDays(30).withNano(0);
        LocalDateTime headFrom = now.minusDays(5).withNano(0);
        MembershipDetail member = membershipService.add(new MembershipCreateRequest(
                apartmentId, memberId, MembershipRole.MEMBER, memberFrom, null, "Member fixture"), null);
        MembershipDetail head = membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(headId, headFrom, null, "Head fixture"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(1).withNano(0);
        ApartmentDeactivationRequest noEffects = new ApartmentDeactivationRequest("Apartment deactivated", null);

        try {
            assertThrows(ApartmentStatusConflictException.class,
                    () -> apartmentStatusService.deactivate(apartmentId, noEffects, null));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartments WHERE id = ?", String.class, apartmentId));
            assertEquals(MembershipStatus.ACTIVE, membershipService.detail(member.id(), null).status());

            ApartmentDeactivationRequest partialEffects = new ApartmentDeactivationRequest("Apartment deactivated",
                    List.of(new MembershipEndRequest(member.id(), effectiveAt, "Member ended")));
            assertThrows(ApartmentStatusConflictException.class,
                    () -> apartmentStatusService.deactivate(apartmentId, partialEffects, null));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartments WHERE id = ?", String.class, apartmentId));

            ApartmentDeactivationRequest invalidSecondEnd = new ApartmentDeactivationRequest("Apartment deactivated",
                    List.of(new MembershipEndRequest(member.id(), effectiveAt, "Member ended"),
                            new MembershipEndRequest(head.id(), headFrom, "Head ended before start")));
            assertThrows(MembershipStateConflictException.class,
                    () -> apartmentStatusService.deactivate(apartmentId, invalidSecondEnd, null));
            assertEquals(List.of("ACTIVE", "ACTIVE"), membershipStatuses(member.id(), head.id()));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartments WHERE id = ?", String.class, apartmentId));

            ApartmentDeactivationRequest completeEffects = new ApartmentDeactivationRequest("Apartment deactivated",
                    List.of(new MembershipEndRequest(member.id(), effectiveAt, "Member ended"),
                            new MembershipEndRequest(head.id(), effectiveAt, "Head ended")));
            assertEquals(ApartmentStatus.INACTIVE,
                    apartmentStatusService.deactivate(apartmentId, completeEffects, null).status());
            assertEquals(List.of("INACTIVE", "INACTIVE"), membershipStatuses(member.id(), head.id()));

            assertEquals(ApartmentStatus.ACTIVE,
                    apartmentStatusService.reactivate(apartmentId,
                            new ApartmentReactivationRequest("Apartment reactivated"), null).status());
            assertEquals(List.of("INACTIVE", "INACTIVE"), membershipStatuses(member.id(), head.id()));

            assertEquals(ApartmentStatus.INACTIVE, apartmentStatusService.deactivate(apartmentId, noEffects, null).status());
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ? "
                            + "AND action = 'APARTMENT_DEACTIVATED'",
                    Integer.class, apartmentId.toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ? "
                            + "AND action = 'APARTMENT_REACTIVATED'",
                    Integer.class, apartmentId.toString()));
        } finally {
            cleanupFixtures(apartmentId, memberId, headId);
        }
    }

    @Test
    void deactivationAuditFailureRollsBackNestedMembershipEndAndApartmentState() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Status Rollback " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR06 Status Rollback Resident " + token, "AHR06-SR-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        MembershipDetail membership = membershipService.add(new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, validFrom, null, "Rollback fixture"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        ApartmentDeactivationRequest request = new ApartmentDeactivationRequest("Apartment deactivated",
                List.of(new MembershipEndRequest(membership.id(), effectiveAt, "Membership ended")));
        doAnswer(invocation -> {
            if ("APARTMENT_DEACTIVATED".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic Apartment audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class,
                    () -> apartmentStatusService.deactivate(apartmentId, request, null));

            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartments WHERE id = ?", String.class, apartmentId));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, membership.id()));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM apartment_memberships WHERE id = ?", LocalDateTime.class, membership.id()));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT lifecycle_changed_at FROM apartment_memberships WHERE id = ?",
                    LocalDateTime.class, membership.id()));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT lifecycle_reason FROM apartment_memberships WHERE id = ?", String.class, membership.id()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'MEMBERSHIP_ENDED'",
                    Integer.class, membership.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT' "
                            + "AND entity_id = ? AND action = 'APARTMENT_DEACTIVATED'",
                    Integer.class, apartmentId.toString()));
        } finally {
            cleanupFixtures(apartmentId, residentId);
        }
    }

    @Test
    void concurrentMembershipAddAndApartmentDeactivationLeaveConsistentState() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 12);
        Long apartmentId = apartmentService.create(
                new ApartmentCreateRequest("AHR06 Concurrent Status " + token, "A-01", null), null).id();
        Long residentId = createResident("AHR06 Concurrent Status Resident " + token, "AHR06-CS-" + token);
        MembershipCreateRequest addRequest = new MembershipCreateRequest(
                apartmentId, residentId, MembershipRole.MEMBER, LocalDateTime.now().minusDays(1).withNano(0),
                null, "Concurrent add");
        ApartmentDeactivationRequest deactivateRequest = new ApartmentDeactivationRequest("Concurrent close", null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<MembershipDetail> add = executor.submit(() -> concurrentAdd(addRequest, ready, start));
        Future<ApartmentDetail> deactivate = executor.submit(() -> concurrentDeactivate(
                apartmentId, deactivateRequest, ready, start));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean membershipAdded = futureSucceeded(add, MembershipStatusConflictException.class);
            boolean apartmentDeactivated = futureSucceeded(deactivate, ApartmentStatusConflictException.class);
            assertTrue(membershipAdded ^ apartmentDeactivated);

            String apartmentStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM apartments WHERE id = ?", String.class, apartmentId);
            int effectiveMemberships = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= NOW() AND (valid_to IS NULL OR valid_to > NOW())",
                    Integer.class, apartmentId);
            if (apartmentDeactivated) {
                assertEquals("INACTIVE", apartmentStatus);
                assertEquals(0, effectiveMemberships);
            } else {
                assertEquals("ACTIVE", apartmentStatus);
                assertEquals(1, effectiveMemberships);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(apartmentId, residentId);
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
            throw new IllegalStateException("Concurrent membership add did not start");
        }
        return membershipService.add(request, null);
    }

    private ApartmentDetail concurrentDeactivate(
            Long apartmentId, ApartmentDeactivationRequest request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent Apartment deactivation did not start");
        }
        return apartmentStatusService.deactivate(apartmentId, request, null);
    }

    private boolean futureSucceeded(Future<?> future, Class<? extends Throwable> expectedFailure) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return true;
        } catch (ExecutionException exception) {
            assertTrue(expectedFailure.isInstance(exception.getCause()));
            return false;
        }
    }

    private List<String> membershipStatuses(Long firstMembershipId, Long secondMembershipId) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM apartment_memberships WHERE id IN (?, ?) ORDER BY id",
                String.class, firstMembershipId, secondMembershipId);
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
}
