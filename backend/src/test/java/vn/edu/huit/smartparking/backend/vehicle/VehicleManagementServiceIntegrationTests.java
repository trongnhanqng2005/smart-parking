package vn.edu.huit.smartparking.backend.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipLifecycleRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipTransferRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipVoidRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipEndRequest;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusConflictException;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerTransferRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.AuthorizedUserGrantRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightLifecycleRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightVoidRequest;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleRightPendingTransition;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleStatus;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleManagementService;
import vn.edu.huit.smartparking.backend.vehicle.service.AuthorizedUserGrantService;
import vn.edu.huit.smartparking.backend.vehicle.service.GuarantorChainConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleOwnerConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightOverlapException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleStatusConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRelationStateConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightLifecycleService;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightPendingTransitionProcessor;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRightPendingTransitionRepository;

@SpringBootTest
@ActiveProfiles("test")
class VehicleManagementServiceIntegrationTests {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private VehicleManagementService vehicleService;

    @Autowired
    private AuthorizedUserGrantService authorizedUserService;

    @Autowired
    private VehicleRightLifecycleService vehicleRightLifecycleService;

    @Autowired
    private VehicleRightPendingTransitionProcessor pendingTransitionProcessor;

    @Autowired
    private VehicleRightPendingTransitionRepository pendingTransitionRepository;

    @Autowired
    private ApartmentManagementService apartmentService;

    @Autowired
    private ResidentManagementService residentService;

    @Autowired
    private ResidentStatusManagementService residentStatusService;

    @Autowired
    private ApartmentMembershipManagementService membershipService;

    @Autowired
    private ApartmentStatusManagementService apartmentStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private AuditService auditService;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void vehicleLookupUsesExactNormalizedPlateAndReturnsMinimizedDetails() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "DISPLAY-" + token, "NORMALIZED-" + token,
                VehicleStatus.ACTIVE);

        var page = vehicleService.list("NORMALIZED-" + token, 0, 20);
        var noMatch = vehicleService.list("DISPLAY-" + token, 0, 20);
        var detail = vehicleService.detail(vehicle.vehicleId(), null);

        assertEquals(1, page.totalItems());
        assertEquals(vehicle.vehicleId(), page.items().getFirst().id());
        assertEquals("DISPLAY-" + token, page.items().getFirst().plateNumber());
        assertEquals(vehicle.categoryId(), page.items().getFirst().vehicleCategoryId());
        assertEquals(0, noMatch.totalItems());
        assertEquals(vehicle.vehicleId(), detail.id());
        assertEquals(VehicleStatus.ACTIVE, detail.status());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE' AND entity_id = ? "
                        + "AND action = 'VEHICLE_DETAIL_READ'",
                Integer.class, vehicle.vehicleId().toString()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerAssignmentAllowsScheduledOwnershipForBlockedResidentAndVehicle() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "BLOCKED-" + token, "NB-" + token,
                VehicleStatus.BLOCKED);
        Long blockedResidentId = createResident("AHR07 Blocked Owner " + token, "AHR07-BO-" + token);
        entityManager.find(Resident.class, blockedResidentId).setStatus(ResidentStatus.BLOCKED);
        entityManager.flush();
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime validTo = validFrom.plusDays(30);

        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(blockedResidentId, validFrom, validTo, "Owner registered"), null);

        assertEquals(vehicle.vehicleId(), owner.vehicleId());
        assertEquals(blockedResidentId, owner.residentId());
        assertEquals(VehicleRelationType.OWNER, owner.relationType());
        assertNull(owner.guarantorType());
        assertNull(owner.guarantorResidentId());
        assertNull(owner.guarantorApartmentId());
        assertEquals(VehicleRelationStatus.ACTIVE, owner.status());
        assertEquals(validFrom, owner.validFrom());
        assertEquals(validTo, owner.validTo());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND entity_id = ? AND action = 'VEHICLE_OWNER_ASSIGNED'",
                Integer.class, owner.id().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicles WHERE id = ?", Integer.class, vehicle.vehicleId()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerAssignmentRejectsAnOverlappingOwnerAndAnInactiveResident() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "OWNER-CONFLICT-" + token, "NO-" + token,
                VehicleStatus.ACTIVE);
        Long firstOwnerId = createResident("AHR07 First Owner " + token, "AHR07-FO-" + token);
        Long secondOwnerId = createResident("AHR07 Second Owner " + token, "AHR07-SO-" + token);
        Long inactiveResidentId = createResident("AHR07 Inactive Owner " + token, "AHR07-IO-" + token);
        entityManager.find(Resident.class, inactiveResidentId).setStatus(ResidentStatus.INACTIVE);
        entityManager.flush();
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(firstOwnerId, validFrom, null, "Initial owner"), null);

        assertThrows(VehicleOwnerConflictException.class, () -> vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(secondOwnerId, validFrom, null, "Conflicting owner"), null));
        assertThrows(VehicleStatusConflictException.class, () -> vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(inactiveResidentId, validFrom, null, "Inactive owner"), null));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                        + "AND relation_type = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, vehicle.vehicleId()));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerAssignmentRejectsOwnerAndAuthorizedUserOverlapForTheSameResidentVehicle() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "RIGHT-OVERLAP-" + token, "NR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR07 Guarantor Owner " + token, "AHR07-GO-" + token);
        Long targetResidentId = createResident("AHR07 Authorized Resident " + token, "AHR07-AU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime validTo = LocalDateTime.now().plusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Existing owner"), null);
        Vehicle ownerVehicle = entityManager.find(Vehicle.class, vehicle.vehicleId());
        var ownerResident = entityManager.getReference(
                vn.edu.huit.smartparking.backend.resident.entity.Resident.class, ownerId);
        var targetResident = entityManager.getReference(
                vn.edu.huit.smartparking.backend.resident.entity.Resident.class, targetResidentId);
        VehicleResidentRelation authorizedUser = new VehicleResidentRelation();
        authorizedUser.setVehicle(ownerVehicle);
        authorizedUser.setResident(targetResident);
        authorizedUser.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        authorizedUser.setGuarantorType(VehicleRelationGuarantorType.OWNER);
        authorizedUser.setGuarantorResident(ownerResident);
        authorizedUser.setValidFrom(validFrom);
        authorizedUser.setValidTo(validTo);
        authorizedUser.setStatus(VehicleRelationStatus.ACTIVE);
        authorizedUser.setCreatedAt(LocalDateTime.now());
        entityManager.persist(authorizedUser);
        entityManager.flush();

        assertThrows(VehicleRightOverlapException.class, () -> vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(targetResidentId, validFrom, validTo, "Overlapping owner"), null));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerTransferSchedulesHistoryAndEffectiveOwnershipAtomically() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "TRANSFER-" + token, "NT-" + token,
                VehicleStatus.INACTIVE);
        Long previousOwnerId = createResident("AHR07 Previous Owner " + token, "AHR07-PO-" + token);
        Long nextOwnerId = createResident("AHR07 Next Owner " + token, "AHR07-NO-" + token);
        entityManager.find(Resident.class, nextOwnerId).setStatus(ResidentStatus.BLOCKED);
        entityManager.flush();
        LocalDateTime validFrom = LocalDateTime.now().minusDays(10).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().plusDays(1).withNano(0);
        VehicleRightDetail previous = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(previousOwnerId, validFrom, null, "Initial owner"), null);

        VehicleRightDetail transferred = vehicleService.transferOwner(vehicle.vehicleId(),
                new VehicleOwnerTransferRequest(previous.id(), nextOwnerId, effectiveAt, "Owner changed"), null);

        assertEquals(VehicleRelationType.OWNER, transferred.relationType());
        assertEquals(nextOwnerId, transferred.residentId());
        assertEquals(VehicleRelationStatus.ACTIVE, transferred.status());
        assertEquals(effectiveAt, transferred.validFrom());
        assertNull(transferred.validTo());
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM vehicle_resident_relations WHERE id = ?", String.class, previous.id()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT lifecycle_changed_at FROM vehicle_resident_relations WHERE id = ?",
                LocalDateTime.class, previous.id()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT lifecycle_reason FROM vehicle_resident_relations WHERE id = ?",
                String.class, previous.id()));
        assertEquals(effectiveAt, jdbcTemplate.queryForObject(
                "SELECT valid_to FROM vehicle_resident_relations WHERE id = ?", LocalDateTime.class, previous.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? AND relation_type = 'OWNER' "
                        + "AND status = 'ACTIVE' AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                Integer.class, vehicle.vehicleId(), effectiveAt, effectiveAt));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND entity_id = ? AND action = 'VEHICLE_OWNER_TRANSFERRED_OUT'",
                Integer.class, previous.id().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND entity_id = ? AND action = 'VEHICLE_OWNER_TRANSFERRED_IN'",
                Integer.class, transferred.id().toString()));
    }

    @Test
    void ownerTransferAuditFailureRollsBackSourceRelationNewOwnerAndTransferHistory() {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "TRANSFER-ROLLBACK-" + token,
                "NTR-" + token, VehicleStatus.ACTIVE);
        Long previousOwnerId = createResident("AHR07 Rollback Previous " + token, "AHR07-RP-" + token);
        Long nextOwnerId = createResident("AHR07 Rollback Next " + token, "AHR07-RN-" + token);
        Long authorizedId = createResident("AHRR04 Rollback Grant " + token, "AHRR04-RG-" + token);
        User actor = createActor("AHRR04-ROLLBACK-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime validFrom = LocalDateTime.now().minusDays(10).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().plusDays(1).withNano(0);
        VehicleRightDetail previous = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(previousOwnerId, validFrom, null, "Initial owner"), null);
        VehicleRightDetail authorized = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.OWNER, previousOwnerId, null,
                        validFrom.plusDays(1), null, "Grant fixture"), null);
        doAnswer(invocation -> {
            if ("VEHICLE_OWNER_TRANSFERRED_IN".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic OWNER transfer audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class, () -> vehicleService.transferOwner(vehicle.vehicleId(),
                    new VehicleOwnerTransferRequest(previous.id(), nextOwnerId, effectiveAt, "Owner changed"), actor));

            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM vehicle_resident_relations WHERE id = ?", LocalDateTime.class, previous.id()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? AND resident_id = ?",
                    Integer.class, vehicle.vehicleId(), nextOwnerId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_OWNER_TRANSFERRED_OUT'",
                    Integer.class, previous.id().toString()));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM vehicle_resident_relations WHERE id = ?",
                    LocalDateTime.class, authorized.id()));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM vehicle_resident_relations WHERE id = ?", String.class, authorized.id()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Integer.class, authorized.id()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle, new Long[] {previousOwnerId, nextOwnerId, authorizedId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void concurrentOwnerAssignmentsSerializeAndLeaveExactlyOneOwner() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 12);
        VehicleFixture vehicle = createVehicle(token, "OWNER-RACE-" + token, "NOR-" + token,
                VehicleStatus.ACTIVE);
        Long firstOwnerId = createResident("AHR07 Race Owner One " + token, "AHR07-RO1-" + token);
        Long secondOwnerId = createResident("AHR07 Race Owner Two " + token, "AHR07-RO2-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusSeconds(2).withNano(0);
        VehicleOwnerAssignmentRequest first = new VehicleOwnerAssignmentRequest(
                firstOwnerId, validFrom, null, "Concurrent assignment");
        VehicleOwnerAssignmentRequest second = new VehicleOwnerAssignmentRequest(
                secondOwnerId, validFrom, null, "Concurrent assignment");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<VehicleRightDetail>> results = List.of(
                executor.submit(() -> concurrentAssignment(vehicle.vehicleId(), first, ready, start)),
                executor.submit(() -> concurrentAssignment(vehicle.vehicleId(), second, ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<VehicleRightDetail> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof VehicleOwnerConflictException);
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'OWNER' AND status = 'ACTIVE' "
                            + "AND valid_from <= NOW() AND (valid_to IS NULL OR valid_to > NOW())",
                    Integer.class, vehicle.vehicleId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(vehicle, firstOwnerId, secondOwnerId);
        }
    }

    @Test
    void grantsAuthorizedUserWithEffectiveOwnerGuarantorAndAuditsActorSeparately() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-GRANT-" + token, "OG-" + token, VehicleStatus.INACTIVE);
        Long ownerId = createResident("AHR08 Owner " + token, "AHR08-O-" + token);
        Long authorizedId = createResident("AHR08 Authorized " + token, "AHR08-A-" + token);
        User actor = createActor("AHR08-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime validTo = validFrom.plusDays(30);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(1), null, "Owner fixture"), null);

        try {
            VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, validTo,
                            "Owner authorized use"), actor);

            assertEquals(VehicleRelationType.AUTHORIZED_USER, grant.relationType());
            assertEquals(VehicleRelationGuarantorType.OWNER, grant.guarantorType());
            assertEquals(ownerId, grant.guarantorResidentId());
            assertNull(grant.guarantorApartmentId());
            assertEquals(validFrom, grant.validFrom());
            assertEquals(validTo, grant.validTo());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND relation_type = 'AUTHORIZED_USER' AND guarantor_type = 'OWNER' "
                            + "AND guarantor_resident_id = ? AND guarantor_apartment_id IS NULL",
                    Integer.class, grant.id(), ownerId));
            String auditData = jdbcTemplate.queryForObject(
                    "SELECT new_data FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED'",
                    String.class, grant.id().toString());
            assertTrue(auditData.contains("guarantor_resident_id"));
            assertTrue(!auditData.contains("identity_number"));
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED'",
                    Long.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void grantsAuthorizedUserWithHouseholdHeadAndMatchingApartmentOwnerMemberships() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-GRANT-" + token, "HG-" + token,
                VehicleStatus.BLOCKED);
        Long ownerId = createResident("AHR08 Household Owner " + token, "AHR08-HO-" + token);
        Long headId = createResident("AHR08 Household Head " + token, "AHR08-HH-" + token);
        Long authorizedId = createResident("AHR08 Household User " + token, "AHR08-HU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(10), null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom.minusDays(10));
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom.minusDays(10));

        try {
            VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, null, "Household head authorized use"), null);

            assertEquals(headId, grant.guarantorResidentId());
            assertEquals(apartmentId, grant.guarantorApartmentId());
            assertEquals(VehicleRelationGuarantorType.HOUSEHOLD_HEAD, grant.guarantorType());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND guarantor_type = 'HOUSEHOLD_HEAD' AND guarantor_resident_id = ? "
                            + "AND guarantor_apartment_id = ?",
                    Integer.class, grant.id(), headId, apartmentId));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void rejectsMissingOrWrongApartmentGuarantorChainAndInactiveParties() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "CHAIN-" + token, "CH-" + token, VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Chain Owner " + token, "AHR08-CO-" + token);
        Long headId = createResident("AHR08 Chain Head " + token, "AHR08-CH-" + token);
        Long authorizedId = createResident("AHR08 Chain User " + token, "AHR08-CU-" + token);
        Long apartmentId = createApartment(token);
        Long otherApartmentId = createApartment(token + "x");
        LocalDateTime validFrom = LocalDateTime.now().plusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(10), null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom.minusDays(10));
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom.minusDays(10));

        try {
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, otherApartmentId,
                            validFrom, null, "Wrong apartment context"), null));
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, headId, null,
                            validFrom, null, "Wrong OWNER guarantor"), null));

            jdbcTemplate.update("UPDATE residents SET status = 'BLOCKED' WHERE id = ?", authorizedId);
            assertThrows(VehicleStatusConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, null, "Blocked authorized resident"), null));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId},
                    new Long[] {apartmentId, otherApartmentId});
        }
    }

    @Test
    void householdHeadGrantRequiresOwnerMembershipInContextAndActiveApartment() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-CONTEXT-" + token, "HC-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Context Owner " + token, "AHR08-XO-" + token);
        Long headId = createResident("AHR08 Context Head " + token, "AHR08-XH-" + token);
        Long authorizedId = createResident("AHR08 Context User " + token, "AHR08-XU-" + token);
        Long apartmentId = createApartment(token);
        Long otherApartmentId = createApartment(token + "y");
        LocalDateTime validFrom = LocalDateTime.now().plusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(10), null, "Owner fixture"), null);
        createMembership(otherApartmentId, ownerId, MembershipRole.MEMBER, validFrom.minusDays(10));
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom.minusDays(10));

        AuthorizedUserGrantRequest request = new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                validFrom, null, "Household head authorization");
        try {
            assertThrows(GuarantorChainConflictException.class,
                    () -> authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(), request, null));

            createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom.minusDays(10));
            jdbcTemplate.update("UPDATE apartments SET status = 'INACTIVE' WHERE id = ?", apartmentId);
            assertThrows(VehicleStatusConflictException.class,
                    () -> authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(), request, null));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId},
                    new Long[] {apartmentId, otherApartmentId});
        }
    }

    @Test
    void ownerGuarantorMustBeEffectiveAtGrantStartAndActiveToActAsGuarantor() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-TIME-" + token, "OT-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Time Owner " + token, "AHR08-TO-" + token);
        Long authorizedId = createResident("AHR08 Time User " + token, "AHR08-TU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, validFrom.minusDays(10), validFrom.minusSeconds(1), "Expiring owner"), null);
        AuthorizedUserGrantRequest request = new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, null, "Owner authorization");

        try {
            assertThrows(GuarantorChainConflictException.class,
                    () -> authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(), request, null));
            vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                    ownerId, validFrom, null, "Renewed owner"), null);
            jdbcTemplate.update("UPDATE residents SET status = 'BLOCKED' WHERE id = ?", ownerId);
            assertThrows(VehicleStatusConflictException.class,
                    () -> authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(), request, null));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
        }
    }

    @Test
    void directOwnerGuarantorMustCoverTheWholeAuthorizedUserInterval() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-INTERVAL-" + token, "OI-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR02 Finite Owner " + token, "AHRR02-FO-" + token);
        Long authorizedId = createResident("AHRR02 Authorized " + token, "AHRR02-AU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime ownerValidTo = validFrom.plusDays(20);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, validFrom.minusDays(5), ownerValidTo, "Finite owner fixture"), null);
        Integer previousGrantAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED' "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(new_data, '$.vehicle_id')) = ?",
                Integer.class, vehicle.vehicleId().toString());

        try {
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null,
                            validFrom, ownerValidTo.plusSeconds(1), "Grant exceeds owner interval"), null));
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null,
                            validFrom, null, "Unbounded grant exceeds owner interval"), null));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId()));
            assertEquals(previousGrantAuditCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED' "
                            + "AND JSON_UNQUOTE(JSON_EXTRACT(new_data, '$.vehicle_id')) = ?",
                    Integer.class, vehicle.vehicleId().toString()));

            VehicleRightDetail boundaryGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null,
                            validFrom, ownerValidTo, "Grant ends at owner boundary"), null);
            assertEquals(ownerValidTo, boundaryGrant.validTo());
            assertEquals(previousGrantAuditCount + 1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED' "
                            + "AND JSON_UNQUOTE(JSON_EXTRACT(new_data, '$.vehicle_id')) = ?",
                    Integer.class, vehicle.vehicleId().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
        }
    }

    @Test
    void householdHeadMembershipMustCoverTheWholeAuthorizedUserInterval() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-INTERVAL-" + token, "HI-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR02 Head Interval Owner " + token, "AHRR02-HIO-" + token);
        Long headId = createResident("AHRR02 Finite Head " + token, "AHRR02-FH-" + token);
        Long authorizedId = createResident("AHRR02 Head Interval User " + token, "AHRR02-HIU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime headValidTo = validFrom.plusDays(20);
        LocalDateTime sourceValidFrom = validFrom.minusDays(10);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, sourceValidFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, sourceValidFrom);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, sourceValidFrom, headValidTo);

        try {
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, headValidTo.plusSeconds(1), "Grant exceeds head membership"), null));
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, null, "Unbounded grant exceeds head membership"), null));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId()));
            assertEquals(0, grantAuditCount(vehicle.vehicleId()));

            VehicleRightDetail boundaryGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, headValidTo, "Grant ends at head membership boundary"), null);
            assertEquals(headValidTo, boundaryGrant.validTo());
            assertEquals(1, grantAuditCount(vehicle.vehicleId()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void householdOwnerMembershipMustCoverTheWholeAuthorizedUserInterval() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-MEM-INTERVAL-" + token, "OMI-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR02 Finite Owner Member " + token, "AHRR02-FOM-" + token);
        Long headId = createResident("AHRR02 Owner Member Head " + token, "AHRR02-OMH-" + token);
        Long authorizedId = createResident("AHRR02 Owner Member User " + token, "AHRR02-OMU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime ownerMembershipValidTo = validFrom.plusDays(20);
        LocalDateTime sourceValidFrom = validFrom.minusDays(10);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, sourceValidFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, sourceValidFrom, ownerMembershipValidTo);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, sourceValidFrom);

        try {
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, ownerMembershipValidTo.plusSeconds(1), "Grant exceeds owner membership"), null));
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, null, "Unbounded grant exceeds owner membership"), null));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId()));
            assertEquals(0, grantAuditCount(vehicle.vehicleId()));

            VehicleRightDetail boundaryGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, ownerMembershipValidTo, "Grant ends at owner membership boundary"), null);
            assertEquals(ownerMembershipValidTo, boundaryGrant.validTo());
            assertEquals(1, grantAuditCount(vehicle.vehicleId()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void householdOwnerRelationMustCoverTheWholeAuthorizedUserInterval() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HOWNER-" + token, "HOI-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR02 Finite Household Owner " + token, "AHRR02-FHO-" + token);
        Long headId = createResident("AHRR02 Owner Relation Head " + token, "AHRR02-ORH-" + token);
        Long authorizedId = createResident("AHRR02 Owner Relation User " + token, "AHRR02-ORU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime ownerValidTo = validFrom.plusDays(20);
        LocalDateTime sourceValidFrom = validFrom.minusDays(10);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, sourceValidFrom, ownerValidTo, "Finite household owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, sourceValidFrom);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, sourceValidFrom);

        try {
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, ownerValidTo.plusSeconds(1), "Grant exceeds household OWNER"), null));
            assertThrows(GuarantorChainConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, null, "Unbounded grant exceeds household OWNER"), null));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId()));
            assertEquals(0, grantAuditCount(vehicle.vehicleId()));

            VehicleRightDetail boundaryGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                    new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            validFrom, ownerValidTo, "Grant ends at household OWNER boundary"), null);
            assertEquals(ownerValidTo, boundaryGrant.validTo());
            assertEquals(1, grantAuditCount(vehicle.vehicleId()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void authorizedUserListAndHistoryAreMinimizedAndDetailReadIsAudited() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "RIGHT-QUERY-" + token, "RQ-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Query Owner " + token, "AHR08-QO-" + token);
        Long authorizedId = createResident("AHR08 Query User " + token, "AHR08-QU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, null, "Query grant"), null);
        int auditsBeforeRead = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND entity_id = ?", Integer.class, grant.id().toString());

        try {
            var page = authorizedUserService.list(null, authorizedId, null, 0, 20);
            authorizedUserService.history(grant.id(), 0, 20);
            assertEquals(1, page.totalItems());
            assertEquals(authorizedId, page.items().getFirst().residentId());
            assertEquals(auditsBeforeRead, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ?", Integer.class, grant.id().toString()));

            VehicleRightDetail detail = authorizedUserService.detail(grant.id(), null);

            assertEquals(grant.id(), detail.id());
            assertEquals(auditsBeforeRead + 1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ?", Integer.class, grant.id().toString()));
            assertEquals("VEHICLE_RIGHT_DETAIL_READ", authorizedUserService.history(grant.id(), 0, 20)
                    .items().getFirst().action());
            assertThrows(VehicleRightOverlapException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, null,
                            "Duplicate overlapping grant"), null));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
        }
    }

    @Test
    void authorizedUserAuditFailureRollsBackGrantAndAudit() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "GRANT-ROLLBACK-" + token, "GR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Rollback Owner " + token, "AHR08-RO-" + token);
        Long authorizedId = createResident("AHR08 Rollback User " + token, "AHR08-RU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(1), null, "Owner fixture"), null);
        doAnswer(invocation -> {
            if ("VEHICLE_AUTHORIZED_USER_GRANTED".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic authorized-user audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                            VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, null,
                            "Authorized user"), null));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? AND resident_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId(), authorizedId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED' "
                            + "AND JSON_UNQUOTE(JSON_EXTRACT(new_data, '$.vehicle_id')) = ?",
                    Integer.class, vehicle.vehicleId().toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupFixtures(vehicle, ownerId, authorizedId);
        }
    }

    @Test
    void concurrentOwnerChainCreationAndAuthorizedGrantNeverCommitAnInvalidChain() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "CHAIN-RACE-" + token, "CR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR08 Race Owner " + token, "AHR08-CRO-" + token);
        Long authorizedId = createResident("AHR08 Race User " + token, "AHR08-CRU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusSeconds(2).withNano(0);
        VehicleOwnerAssignmentRequest ownerRequest = new VehicleOwnerAssignmentRequest(
                ownerId, validFrom, null, "Concurrent owner chain");
        AuthorizedUserGrantRequest grantRequest = new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, null, "Concurrent grant");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<VehicleRightDetail> owner = executor.submit(() -> concurrentAssignment(
                vehicle.vehicleId(), ownerRequest, ready, start));
        Future<VehicleRightDetail> grant = executor.submit(() -> concurrentGrant(
                vehicle.vehicleId(), grantRequest, ready, start));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            owner.get(10, TimeUnit.SECONDS);
            boolean grantSucceeded;
            try {
                grant.get(10, TimeUnit.SECONDS);
                grantSucceeded = true;
            } catch (ExecutionException exception) {
                assertTrue(exception.getCause() instanceof GuarantorChainConflictException);
                grantSucceeded = false;
            }
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'OWNER' AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, vehicle.vehicleId(), validFrom, validFrom));
            assertEquals(grantSucceeded ? 1 : 0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND resident_id = ? AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId(), authorizedId));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupFixtures(vehicle, ownerId, authorizedId);
        }
    }

    @Test
    void synchronizedOwnerTransferAndGrantLeaveNoAuthorizedRightPastTheAuthorityLoss() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "TRANSFER-GRANT-RACE-" + token, "TGR-" + token,
                VehicleStatus.ACTIVE);
        Long oldOwnerId = createResident("AHR09 Race Former Owner " + token, "AHR09-RFO-" + token);
        Long newOwnerId = createResident("AHR09 Race New Owner " + token, "AHR09-RNO-" + token);
        Long authorizedId = createResident("AHR09 Race Authorized " + token, "AHR09-RAU-" + token);
        User actor = createActor("AHRR04-RACE-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                oldOwnerId, transferAt.minusDays(30), null, "Initial owner"), null);
        VehicleOwnerTransferRequest transferRequest = new VehicleOwnerTransferRequest(
                owner.id(), newOwnerId, transferAt, "Owner authority transferred");
        AuthorizedUserGrantRequest grantRequest = new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                VehicleRelationGuarantorType.OWNER, oldOwnerId, null,
                LocalDateTime.now().minusSeconds(2).withNano(0), null, "Concurrent grant");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<VehicleRightDetail> transfer = executor.submit(() -> concurrentOwnerTransfer(
                vehicle.vehicleId(), transferRequest, actor, ready, start));
        Future<VehicleRightDetail> grant = executor.submit(() -> concurrentGrant(
                vehicle.vehicleId(), grantRequest, ready, start));

        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            VehicleRightDetail successor = transfer.get(15, TimeUnit.SECONDS);
            VehicleRightDetail createdGrant = null;
            try {
                createdGrant = grant.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                assertTrue(exception.getCause() instanceof GuarantorChainConflictException);
            }

            assertEquals(newOwnerId, successor.residentId());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'OWNER' AND status = 'ACTIVE' AND valid_from <= ? "
                            + "AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, vehicle.vehicleId(), transferAt, transferAt));
            assertEquals(newOwnerId, jdbcTemplate.queryForObject(
                    "SELECT resident_id FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND relation_type = 'OWNER' AND status = 'ACTIVE' AND valid_from <= ? "
                            + "AND (valid_to IS NULL OR valid_to > ?)",
                    Long.class, vehicle.vehicleId(), transferAt, transferAt));
            assertEquals(createdGrant == null ? 0 : 1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? AND resident_id = ? "
                            + "AND relation_type = 'AUTHORIZED_USER'",
                    Integer.class, vehicle.vehicleId(), authorizedId));
            if (createdGrant != null) {
                VehicleRightDetail scheduled = authorizedUserService.detail(createdGrant.id(), null);
                assertEquals(VehicleRelationStatus.ACTIVE, scheduled.status());
                assertEquals(transferAt, scheduled.validTo());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ? "
                                + "AND effective_at = ? AND source_actor_user_id = ?",
                        Integer.class, createdGrant.id(), transferAt, actorId));
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                                + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                        Integer.class, createdGrant.id(), transferAt.minusSeconds(1), transferAt.minusSeconds(1)));
                assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                                + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                        Integer.class, createdGrant.id(), transferAt, transferAt));
            } else {
                assertEquals(0, pendingTransitionRepository.count());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupGrantFixtures(vehicle, new Long[] {oldOwnerId, newOwnerId, authorizedId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void authorizedUserEndRecordsTheRequestedEndAndAuditsTheLifecycleChange() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "END-RIGHT-" + token, "ER-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 End Owner " + token, "AHR09-EO-" + token);
        Long authorizedId = createResident("AHR09 End User " + token, "AHR09-EU-" + token);
        Long revokedResidentId = createResident("AHR09 Revoked User " + token, "AHR09-RU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(5).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, validFrom.minusDays(2), null, "Owner fixture"), null);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, null, "Grant fixture"), null);
        VehicleRightDetail revokedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), revokedResidentId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom, null, "Grant fixture"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);

        try {
            VehicleRightDetail ended = vehicleRightLifecycleService.end(grant.id(),
                    new VehicleRightLifecycleRequest(effectiveAt, "Authorization ended"), null);
            VehicleRightDetail revoked = vehicleRightLifecycleService.revoke(revokedGrant.id(),
                    new VehicleRightLifecycleRequest(effectiveAt, "Authorization revoked"), null);

            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(effectiveAt, ended.validTo());
            assertEquals("Authorization ended", ended.lifecycleReason());
            assertTrue(ended.lifecycleChangedAt().isAfter(effectiveAt));
            assertEquals(VehicleRelationStatus.REVOKED, revoked.status());
            assertEquals(effectiveAt, revoked.validTo());
            assertEquals("Authorization revoked", revoked.lifecycleReason());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_ENDED'",
                    Integer.class, grant.id().toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_REVOKED'",
                    Integer.class, revokedGrant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId, revokedResidentId}, new Long[0]);
        }
    }

    @Test
    void directVehicleRightLifecycleRejectsPreStartRevokeAndFutureEffectiveCommands() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "REVOKE-RIGHT-" + token, "RR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Revoke Owner " + token, "AHR09-RO-" + token);
        Long authorizedId = createResident("AHR09 Revoke User " + token, "AHR09-RU-" + token);
        Long startedEndUserId = createResident("AHR09 Future End User " + token, "AHR09-FEU-" + token);
        Long startedRevokeUserId = createResident("AHR09 Future Revoke User " + token, "AHR09-FRU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(5).withNano(0);
        LocalDateTime startedAt = LocalDateTime.now().minusDays(5).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, startedAt.minusDays(2), null, "Owner fixture"), null);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, null, "Grant fixture"), null);
        VehicleRightDetail startedEndGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), startedEndUserId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, startedAt, null, "Grant fixture"), null);
        VehicleRightDetail startedRevokeGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), startedRevokeUserId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, startedAt, null, "Grant fixture"), null);

        try {
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.revoke(
                    grant.id(), new VehicleRightLifecycleRequest(LocalDateTime.now(), "Premature revoke"), null));
            LocalDateTime futureEffectiveAt = LocalDateTime.now().plusDays(1);
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.end(
                    startedEndGrant.id(), new VehicleRightLifecycleRequest(futureEffectiveAt, "Future end"), null));
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.revoke(
                    startedRevokeGrant.id(), new VehicleRightLifecycleRequest(futureEffectiveAt, "Future revoke"), null));
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(grant.id(), null).status());
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(startedEndGrant.id(), null).status());
            assertEquals(VehicleRelationStatus.ACTIVE,
                    authorizedUserService.detail(startedRevokeGrant.id(), null).status());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_REVOKED'",
                    Integer.class, grant.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action IN ('VEHICLE_RIGHT_ENDED','VEHICLE_RIGHT_REVOKED')",
                    Integer.class, startedEndGrant.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action IN ('VEHICLE_RIGHT_ENDED','VEHICLE_RIGHT_REVOKED')",
                    Integer.class, startedRevokeGrant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {ownerId, authorizedId, startedEndUserId, startedRevokeUserId}, new Long[0]);
        }
    }

    @Test
    void ownerTransferEndsStartedGrantsAndCancelsGrantsStartingAtTheTransferInstant() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "LOSS-TRANSFER-" + token, "LT-" + token,
                VehicleStatus.ACTIVE);
        Long oldOwnerId = createResident("AHR09 Former Owner " + token, "AHR09-FOO-" + token);
        Long newOwnerId = createResident("AHR09 Successor Owner " + token, "AHR09-SO-" + token);
        Long startedUserId = createResident("AHR09 Started Grant User " + token, "AHR09-SGU-" + token);
        Long scheduledUserId = createResident("AHR09 Scheduled Grant User " + token, "AHR09-FGU-" + token);
        User actor = createActor("AHRR04-OWNER-TRANSFER-" + token);
        Long actorId = actor.getId();
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                oldOwnerId, transferAt.minusDays(30), null, "Initial owner"), null);
        VehicleRightDetail startedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), startedUserId, VehicleRelationGuarantorType.OWNER,
                        oldOwnerId, null, transferAt.minusDays(1), null, "Started grant"), null);
        VehicleRightDetail scheduledGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledUserId,
                        VehicleRelationGuarantorType.OWNER, oldOwnerId, null, transferAt, null,
                        "Grant at transfer boundary"), null);

        try {
            vehicleService.transferOwner(vehicle.vehicleId(), new VehicleOwnerTransferRequest(
                    owner.id(), newOwnerId, transferAt, "Owner authority transferred"), actor);

            VehicleRightDetail stillEffective = authorizedUserService.detail(startedGrant.id(), null);
            VehicleRightDetail cancelled = authorizedUserService.detail(scheduledGrant.id(), null);
            assertEquals(VehicleRelationStatus.ACTIVE, stillEffective.status());
            assertEquals(transferAt, stillEffective.validTo());
            assertNull(stillEffective.lifecycleChangedAt());
            assertNull(stillEffective.lifecycleReason());
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelled.status());
            assertNull(cancelled.validTo());
            assertEquals(transferAt, cancelled.lifecycleChangedAt());
            assertEquals("Owner authority transferred", cancelled.lifecycleReason());
            Long pendingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Long.class, startedGrant.id());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT source_actor_user_id FROM vehicle_right_pending_transitions WHERE id = ?",
                    Long.class, pendingId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Integer.class, startedGrant.id().toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_CANCELLED'",
                    Integer.class, scheduledGrant.id().toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, startedGrant.id(), transferAt.minusSeconds(1), transferAt.minusSeconds(1)));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, startedGrant.id(), transferAt, transferAt));

            assertFalse(pendingTransitionProcessor.processPendingTransition(pendingId, transferAt.minusSeconds(1)));
            assertTrue(pendingTransitionProcessor.processPendingTransition(pendingId, transferAt));
            VehicleRightDetail ended = authorizedUserService.detail(startedGrant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(transferAt, ended.validTo());
            assertEquals(transferAt, ended.lifecycleChangedAt());
            assertEquals("Owner authority transferred", ended.lifecycleReason());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Long.class, startedGrant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {oldOwnerId, newOwnerId, startedUserId, scheduledUserId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void futureOwnerTransferDefersHouseholdHeadGuarantorGrantUntilTheOwnerLossTime() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HOLD-" + token, "HL-" + token, VehicleStatus.ACTIVE);
        Long oldOwnerId = createResident("AHRR04 Household Owner " + token, "AHRR04-HO-" + token);
        Long newOwnerId = createResident("AHRR04 New Household Owner " + token, "AHRR04-HN-" + token);
        Long headId = createResident("AHRR04 Guarantor Head " + token, "AHRR04-GH-" + token);
        Long authorizedId = createResident("AHRR04 Household Grant User " + token, "AHRR04-HU-" + token);
        Long apartmentId = createApartment(token);
        User actor = createActor("AHRR04-HOUSEHOLD-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                oldOwnerId, transferAt.minusDays(30), null, "Initial owner"), null);
        createMembership(apartmentId, oldOwnerId, MembershipRole.MEMBER, transferAt.minusDays(20));
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, transferAt.minusDays(20));
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        transferAt.minusDays(1), null, "Household head grant"), null);
        Long ownerRelationId = jdbcTemplate.queryForObject(
                "SELECT id FROM vehicle_resident_relations WHERE vehicle_id = ? "
                        + "AND relation_type = 'OWNER' AND resident_id = ?",
                Long.class, vehicle.vehicleId(), oldOwnerId);

        try {
            vehicleService.transferOwner(vehicle.vehicleId(), new VehicleOwnerTransferRequest(
                    ownerRelationId, newOwnerId, transferAt, "Owner authority transferred"), actor);

            VehicleRightDetail scheduled = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.ACTIVE, scheduled.status());
            assertEquals(transferAt, scheduled.validTo());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ? "
                            + "AND effective_at = ? AND source_actor_user_id = ?",
                    Integer.class, grant.id(), transferAt, actorId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, grant.id(), transferAt, transferAt));

            Long pendingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Long.class, grant.id());
            assertTrue(pendingTransitionProcessor.processPendingTransition(pendingId, transferAt));
            VehicleRightDetail ended = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(transferAt, ended.lifecycleChangedAt());
            assertEquals("Owner authority transferred", ended.lifecycleReason());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Long.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {oldOwnerId, newOwnerId, headId, authorizedId},
                    new Long[] {apartmentId});
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void endingOwnerRightEndsAndCancelsItsDependentAuthorizedUsers() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-LOSS-END-" + token, "OLE-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Ended Owner " + token, "AHR09-EOO-" + token);
        Long startedUserId = createResident("AHR09 Owner End Started User " + token, "AHR09-OESU-" + token);
        Long scheduledUserId = createResident("AHR09 Owner End Scheduled User " + token, "AHR09-OESD-" + token);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ownerFrom = now.minusDays(30).withNano(0);
        LocalDateTime effectiveAt = now.minusSeconds(1).withNano(0);
        LocalDateTime scheduledFrom = now.plusDays(3).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, ownerFrom, null, "Owner fixture"), null);
        VehicleRightDetail startedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), startedUserId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, ownerFrom.plusDays(2), null, "Started grant"), null);
        VehicleRightDetail scheduledGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledUserId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, scheduledFrom, null,
                        "Scheduled grant"), null);

        try {
            VehicleRightDetail endedOwner = vehicleRightLifecycleService.end(owner.id(),
                    new VehicleRightLifecycleRequest(effectiveAt, "Owner authority withdrawn"), null);
            VehicleRightDetail endedGrant = authorizedUserService.detail(startedGrant.id(), null);
            VehicleRightDetail cancelledGrant = authorizedUserService.detail(scheduledGrant.id(), null);

            assertEquals(VehicleRelationStatus.INACTIVE, endedOwner.status());
            assertEquals(VehicleRelationStatus.INACTIVE, endedGrant.status());
            assertEquals(effectiveAt, endedGrant.validTo());
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelledGrant.status());
            assertNull(cancelledGrant.validTo());
            assertEquals(effectiveAt, cancelledGrant.lifecycleChangedAt());
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, startedUserId, scheduledUserId}, new Long[0]);
        }
    }

    @Test
    void endingVehicleOwnersApartmentMembershipEndsHouseholdHeadGuaranteedRights() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "OWNER-MEMBER-LOSS-" + token, "OML-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Member-loss Owner " + token, "AHR09-MLO-" + token);
        Long headId = createResident("AHR09 Member-loss Head " + token, "AHR09-MLH-" + token);
        Long authorizedId = createResident("AHR09 Member-loss User " + token, "AHR09-MLU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime grantFrom = LocalDateTime.now().minusDays(5).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        Long ownerMembershipId = createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, grantFrom, null,
                        "Household grant"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);

        try {
            membershipService.end(ownerMembershipId,
                    new MembershipLifecycleRequest(effectiveAt, "Vehicle owner household membership ended"), null);

            VehicleRightDetail ended = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(effectiveAt, ended.validTo());
            assertEquals(effectiveAt, ended.lifecycleChangedAt());
            assertEquals("Vehicle owner household membership ended", ended.lifecycleReason());
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void futureHouseholdHeadTransferDefersExistingGrantAndCancelsGrantAtBoundary() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-TRANSFER-LOSS-" + token, "HTL-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Transfer Owner " + token, "AHR09-TO-" + token);
        Long formerHeadId = createResident("AHR09 Former Head " + token, "AHR09-FH-" + token);
        Long successorHeadId = createResident("AHR09 Successor Head " + token, "AHR09-SH-" + token);
        Long startedAuthorizedId = createResident("AHRR05 Started Grant User " + token, "AHRR05-SU-" + token);
        Long scheduledAuthorizedId = createResident("AHRR05 Scheduled Grant User " + token, "AHRR05-SC-" + token);
        Long apartmentId = createApartment(token);
        User actor = createActor("AHRR05-HEAD-TRANSFER-" + token);
        Long actorId = actor.getId();
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long formerHeadMembershipId = createMembership(
                apartmentId, formerHeadId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail startedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), startedAuthorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, formerHeadId, apartmentId,
                        transferAt.minusDays(1), null, "Started head transfer grant"), null);
        VehicleRightDetail scheduledGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledAuthorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, formerHeadId, apartmentId, transferAt, null,
                        "Head transfer boundary grant"), null);

        try {
            membershipService.transferHouseholdHead(apartmentId,
                    new MembershipTransferRequest(formerHeadMembershipId, successorHeadId, transferAt,
                            "Household head transferred"), actor);

            VehicleRightDetail stillEffective = authorizedUserService.detail(startedGrant.id(), null);
            assertEquals(VehicleRelationStatus.ACTIVE, stillEffective.status());
            assertEquals(transferAt, stillEffective.validTo());
            assertNull(stillEffective.lifecycleChangedAt());
            assertNull(stillEffective.lifecycleReason());
            Long pendingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Long.class, startedGrant.id());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT source_actor_user_id FROM vehicle_right_pending_transitions WHERE id = ?",
                    Long.class, pendingId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, startedGrant.id(), transferAt.minusSeconds(1), transferAt.minusSeconds(1)));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, startedGrant.id(), transferAt, transferAt));

            VehicleRightDetail cancelled = authorizedUserService.detail(scheduledGrant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelled.status());
            assertNull(cancelled.validTo());
            assertEquals(transferAt, cancelled.lifecycleChangedAt());
            assertEquals("Household head transferred", cancelled.lifecycleReason());
            assertFalse(pendingTransitionProcessor.processPendingTransition(pendingId, transferAt.minusSeconds(1)));
            assertTrue(pendingTransitionProcessor.processPendingTransition(pendingId, transferAt));
            VehicleRightDetail ended = authorizedUserService.detail(startedGrant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(transferAt, ended.lifecycleChangedAt());
            assertEquals("Household head transferred", ended.lifecycleReason());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Long.class, startedGrant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {ownerId, formerHeadId, successorHeadId, startedAuthorizedId, scheduledAuthorizedId},
                    new Long[] {apartmentId});
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void earliestPendingAuthorityLossIsPreservedWhenSubmittedFirst() {
        assertEarliestPendingAuthorityLoss(false);
    }

    @Test
    void earlierAuthorityLossReplacesPendingTransitionWhenSubmittedSecond() {
        assertEarliestPendingAuthorityLoss(true);
    }

    private void assertEarliestPendingAuthorityLoss(boolean submitLaterLossFirst) {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "COALESCE-" + token, "CO-" + token,
                VehicleStatus.ACTIVE);
        Long oldOwnerId = createResident("AHRR Pending Old Owner " + token, "AHRR-PO-" + token);
        Long newOwnerId = createResident("AHRR Pending New Owner " + token, "AHRR-PN-" + token);
        Long formerHeadId = createResident("AHRR Pending Former Head " + token, "AHRR-PF-" + token);
        Long successorHeadId = createResident("AHRR Pending Successor Head " + token, "AHRR-PS-" + token);
        Long authorizedId = createResident("AHRR Pending User " + token, "AHRR-PU-" + token);
        Long apartmentId = createApartment(token);
        User ownerActor = createActor("AHRR-PENDING-OWNER-" + token);
        User headActor = createActor("AHRR-PENDING-HEAD-" + token);
        Long ownerActorId = ownerActor.getId();
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime ownerLossAt = LocalDateTime.now().plusDays(3).withNano(0);
        LocalDateTime headLossAt = ownerLossAt.plusDays(3);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                oldOwnerId, membershipFrom, null, "Initial owner"), null);
        createMembership(apartmentId, oldOwnerId, MembershipRole.MEMBER, membershipFrom);
        Long headMembershipId = createMembership(
                apartmentId, formerHeadId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, formerHeadId, apartmentId,
                        membershipFrom.plusDays(1), null, "Household grant"), null);
        Long ownerRelationId = jdbcTemplate.queryForObject(
                "SELECT id FROM vehicle_resident_relations WHERE vehicle_id = ? "
                        + "AND relation_type = 'OWNER' AND resident_id = ?",
                Long.class, vehicle.vehicleId(), oldOwnerId);

        try {
            Runnable earlierOwnerLoss = () -> vehicleService.transferOwner(vehicle.vehicleId(),
                    new VehicleOwnerTransferRequest(ownerRelationId, newOwnerId, ownerLossAt,
                            "Earlier owner loss"), ownerActor);
            Runnable laterHeadLoss = () -> membershipService.transferHouseholdHead(apartmentId,
                    new MembershipTransferRequest(headMembershipId, successorHeadId, headLossAt,
                            "Later household-head loss"), headActor);
            if (submitLaterLossFirst) {
                laterHeadLoss.run();
                earlierOwnerLoss.run();
            } else {
                earlierOwnerLoss.run();
                laterHeadLoss.run();
            }

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Integer.class, grant.id()));
            assertEquals(ownerLossAt, jdbcTemplate.queryForObject(
                    "SELECT effective_at FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    LocalDateTime.class, grant.id()));
            assertEquals("Earlier owner loss", jdbcTemplate.queryForObject(
                    "SELECT reason FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    String.class, grant.id()));
            assertEquals(ownerActorId, jdbcTemplate.queryForObject(
                    "SELECT source_actor_user_id FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Long.class, grant.id()));
            VehicleRightDetail scheduled = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.ACTIVE, scheduled.status());
            assertEquals(ownerLossAt, scheduled.validTo());

            Long pendingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Long.class, grant.id());
            assertTrue(pendingTransitionProcessor.processPendingTransition(pendingId, ownerLossAt));
            VehicleRightDetail ended = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals(ownerLossAt, ended.lifecycleChangedAt());
            assertEquals("Earlier owner loss", ended.lifecycleReason());
            assertEquals(ownerActorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Long.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {oldOwnerId, newOwnerId, formerHeadId, successorHeadId, authorizedId},
                    new Long[] {apartmentId});
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", ownerActorId, headActor.getId());
        }
    }

    @Test
    void householdHeadTransferAuditFailureRollsBackPendingVehicleRightEffect() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-ROLLBACK-" + token, "HRB-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR05 Rollback Owner " + token, "AHRR05-RO-" + token);
        Long formerHeadId = createResident("AHRR05 Rollback Former Head " + token, "AHRR05-RF-" + token);
        Long successorHeadId = createResident("AHRR05 Rollback Successor " + token, "AHRR05-RS-" + token);
        Long authorizedId = createResident("AHRR05 Rollback User " + token, "AHRR05-RU-" + token);
        Long apartmentId = createApartment(token);
        User actor = createActor("AHRR05-ROLLBACK-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long formerHeadMembershipId = createMembership(
                apartmentId, formerHeadId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, formerHeadId, apartmentId,
                        transferAt.minusDays(1), null, "Household grant"), null);
        doAnswer(invocation -> {
            if ("HOUSEHOLD_HEAD_TRANSFERRED_IN".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic household-head transfer audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class, () -> membershipService.transferHouseholdHead(apartmentId,
                    new MembershipTransferRequest(formerHeadMembershipId, successorHeadId, transferAt,
                            "Household head transferred"), actor));

            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM apartment_memberships WHERE id = ?",
                    LocalDateTime.class, formerHeadMembershipId));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM vehicle_resident_relations WHERE id = ?",
                    LocalDateTime.class, grant.id()));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM vehicle_resident_relations WHERE id = ?", String.class, grant.id()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ?",
                    Integer.class, grant.id()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND resident_id = ?",
                    Integer.class, apartmentId, successorHeadId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'HOUSEHOLD_HEAD_TRANSFERRED_OUT'",
                    Integer.class, formerHeadMembershipId.toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle,
                    new Long[] {ownerId, formerHeadId, successorHeadId, authorizedId}, new Long[] {apartmentId});
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void concurrentHouseholdHeadTransferAndGrantPreserveAuthorityUntilTransferTime() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-GRANT-RACE-" + token, "HGR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR05 Race Owner " + token, "AHRR05-GO-" + token);
        Long formerHeadId = createResident("AHRR05 Race Former Head " + token, "AHRR05-GF-" + token);
        Long successorHeadId = createResident("AHRR05 Race Successor " + token, "AHRR05-GS-" + token);
        Long authorizedId = createResident("AHRR05 Race User " + token, "AHRR05-GU-" + token);
        Long apartmentId = createApartment(token);
        User actor = createActor("AHRR05-RACE-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime grantFrom = LocalDateTime.now().minusSeconds(2).withNano(0);
        LocalDateTime transferAt = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long formerHeadMembershipId = createMembership(
                apartmentId, formerHeadId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        MembershipTransferRequest transferRequest = new MembershipTransferRequest(
                formerHeadMembershipId, successorHeadId, transferAt, "Household head transferred");
        AuthorizedUserGrantRequest grantRequest = new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                VehicleRelationGuarantorType.HOUSEHOLD_HEAD, formerHeadId, apartmentId,
                grantFrom, null, "Concurrent household grant");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<MembershipDetail> transfer = executor.submit(() -> concurrentHouseholdHeadTransfer(
                apartmentId, transferRequest, actor, ready, start));
        Future<VehicleRightDetail> grantAttempt = executor.submit(() -> concurrentGrant(
                vehicle.vehicleId(), grantRequest, ready, start));

        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            MembershipDetail successor = transfer.get(15, TimeUnit.SECONDS);
            VehicleRightDetail createdGrant = null;
            try {
                createdGrant = grantAttempt.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                assertTrue(exception.getCause() instanceof GuarantorChainConflictException);
            }

            assertEquals(successorHeadId, successor.residentId());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND member_role = 'HOUSEHOLD_HEAD' "
                            + "AND status = 'ACTIVE' AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, apartmentId, transferAt, transferAt));
            if (createdGrant == null) {
                assertEquals(0, pendingTransitionRepository.count());
            } else {
                assertEquals(VehicleRelationStatus.ACTIVE,
                        authorizedUserService.detail(createdGrant.id(), null).status());
                assertEquals(transferAt, authorizedUserService.detail(createdGrant.id(), null).validTo());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE vehicle_right_id = ? "
                                + "AND effective_at = ? AND source_actor_user_id = ?",
                        Integer.class, createdGrant.id(), transferAt, actorId));
                assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                                + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                        Integer.class, createdGrant.id(), transferAt, transferAt));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupGrantFixtures(vehicle,
                    new Long[] {ownerId, formerHeadId, successorHeadId, authorizedId}, new Long[] {apartmentId});
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void apartmentDeactivationInvalidatesScheduledHouseholdGuarantorGrantsWithItsMembershipEnds() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "APARTMENT-LOSS-" + token, "AL-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Deactivated Apartment Owner " + token, "AHR09-DAO-" + token);
        Long headId = createResident("AHR09 Deactivated Apartment Head " + token, "AHR09-DAH-" + token);
        Long authorizedId = createResident("AHR09 Deactivated Apartment User " + token, "AHR09-DAU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);
        LocalDateTime grantFrom = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        Long ownerMembershipId = createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, grantFrom, null,
                        "Apartment closure grant"), null);

        try {
            apartmentStatusService.deactivate(apartmentId, new ApartmentDeactivationRequest("Apartment closed",
                    List.of(new MembershipEndRequest(ownerMembershipId, effectiveAt, "Owner membership ended"),
                            new MembershipEndRequest(headMembershipId, effectiveAt, "Head membership ended"))), null);

            VehicleRightDetail cancelled = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelled.status());
            assertNull(cancelled.validTo());
            assertEquals(effectiveAt, cancelled.lifecycleChangedAt());
            assertEquals("Owner membership ended", cancelled.lifecycleReason());
            assertEquals(ApartmentStatus.INACTIVE, apartmentService.detail(apartmentId, null).status());
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void membershipAuditFailureRollsBackDependentVehicleRightInvalidation() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "CROSS-ROLLBACK-" + token, "CRB-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Rollback Owner " + token, "AHR09-CRO-" + token);
        Long headId = createResident("AHR09 Rollback Head " + token, "AHR09-CRH-" + token);
        Long authorizedId = createResident("AHR09 Rollback User " + token, "AHR09-CRU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime grantFrom = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, grantFrom, null,
                        "Household grant"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);
        doAnswer(invocation -> {
            if ("MEMBERSHIP_ENDED".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("Synthetic membership audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                nullable(String.class), nullable(String.class));

        try {
            assertThrows(IllegalStateException.class, () -> membershipService.end(headMembershipId,
                    new MembershipLifecycleRequest(effectiveAt, "Household head authority ended"), null));

            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, headMembershipId));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM apartment_memberships WHERE id = ?", LocalDateTime.class, headMembershipId));
            assertEquals(VehicleRelationStatus.ACTIVE,
                    authorizedUserService.detail(grant.id(), null).status());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_CANCELLED'",
                    Integer.class, grant.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'MEMBERSHIP_ENDED'",
                    Integer.class, headMembershipId.toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void endingHouseholdHeadMembershipCancelsItsScheduledGuarantorGrant() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "HEAD-LOSS-" + token, "HL-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR09 Head Loss Owner " + token, "AHR09-HLO-" + token);
        Long headId = createResident("AHR09 Head Loss Head " + token, "AHR09-HLH-" + token);
        Long authorizedId = createResident("AHR09 Head Loss User " + token, "AHR09-HLU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime membershipFrom = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime grantFrom = LocalDateTime.now().plusDays(3).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, membershipFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, membershipFrom);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, membershipFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, grantFrom, null,
                        "Household grant"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(1).withNano(0);

        try {
            membershipService.end(headMembershipId,
                    new MembershipLifecycleRequest(effectiveAt, "Household-head authority ended"), null);

            VehicleRightDetail cancelled = authorizedUserService.detail(grant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelled.status());
            assertNull(cancelled.validTo());
            assertEquals(effectiveAt, cancelled.lifecycleChangedAt());
            assertEquals("Household-head authority ended", cancelled.lifecycleReason());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_CANCELLED'",
                    Integer.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void blockingOwnerPreservesOwnershipButEndsItsGuarantorGrant() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "BLOCKED-GUARANTOR-" + token, "BG-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR10 Blocked Guarantor " + token, "AHR10-BG-" + token);
        Long authorizedId = createResident("AHR10 Blocked Guarantor User " + token, "AHR10-BGU-" + token);
        Long headId = createResident("AHR10 Blocked Owner Household Head " + token, "AHR10-BOHH-" + token);
        Long householdAuthorizedId = createResident("AHR10 Blocked Owner Household User " + token,
                "AHR10-BOHU-" + token);
        Long scheduledAuthorizedId = createResident("AHR10 Scheduled Owner Grant User " + token,
                "AHR10-SOGU-" + token);
        Long scheduledHouseholdAuthorizedId = createResident("AHR10 Scheduled Household Grant User " + token,
                "AHR10-SHGU-" + token);
        Long afterBlockAuthorizedId = createResident("AHR10 Post-block User " + token, "AHR10-PBU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(5).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(), new VehicleOwnerAssignmentRequest(
                ownerId, validFrom.minusDays(5), null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom.minusDays(5));
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom.minusDays(5));
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, null, "Guarantor fixture"), null);
        VehicleRightDetail householdGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), householdAuthorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, validFrom, null,
                        "Household guarantor fixture"), null);
        LocalDateTime scheduledFrom = validFrom.plusDays(10);
        VehicleRightDetail scheduledGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledAuthorizedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, scheduledFrom, null,
                        "Scheduled owner guarantor fixture"), null);
        VehicleRightDetail scheduledHouseholdGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledHouseholdAuthorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId, scheduledFrom, null,
                        "Scheduled household guarantor fixture"), null);

        try {
            ResidentDetail blocked = residentStatusService.changeStatus(ownerId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Guarantor blocked", null, null), null);

            assertEquals(ResidentStatus.BLOCKED, blocked.status());
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(owner.id(), null).status());
            VehicleRightDetail endedGrant = authorizedUserService.detail(grant.id(), null);
            VehicleRightDetail endedHouseholdGrant = authorizedUserService.detail(householdGrant.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, endedGrant.status());
            assertEquals(blocked.updatedAt(), endedGrant.validTo());
            assertEquals(blocked.updatedAt(), endedGrant.lifecycleChangedAt());
            assertEquals("Guarantor blocked", endedGrant.lifecycleReason());
            assertEquals(VehicleRelationStatus.INACTIVE, endedHouseholdGrant.status());
            assertEquals(blocked.updatedAt(), endedHouseholdGrant.validTo());
            assertEquals(blocked.updatedAt(), endedHouseholdGrant.lifecycleChangedAt());
            VehicleRightDetail cancelledGrant = authorizedUserService.detail(scheduledGrant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelledGrant.status());
            assertNull(cancelledGrant.validTo());
            assertEquals(blocked.updatedAt(), cancelledGrant.lifecycleChangedAt());
            VehicleRightDetail cancelledHouseholdGrant = authorizedUserService.detail(scheduledHouseholdGrant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelledHouseholdGrant.status());
            assertNull(cancelledHouseholdGrant.validTo());
            assertEquals(blocked.updatedAt(), cancelledHouseholdGrant.lifecycleChangedAt());
            assertThrows(VehicleStatusConflictException.class, () -> authorizedUserService.grantAuthorizedUser(
                    vehicle.vehicleId(), new AuthorizedUserGrantRequest(vehicle.vehicleId(), afterBlockAuthorizedId,
                            VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                            LocalDateTime.now().plusDays(2).withNano(0), null, "Blocked owner chain"), null));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                            + "AND action = 'RESIDENT_STATUS_CHANGED'",
                    Integer.class, ownerId.toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId, headId, householdAuthorizedId,
                    scheduledAuthorizedId, scheduledHouseholdAuthorizedId, afterBlockAuthorizedId},
                    new Long[] {apartmentId});
        }
    }

    @Test
    void inactiveResidentRequiresExplicitMembershipAndVehicleRightActions() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "INACTIVE-GUARD-" + token, "IG-" + token,
                VehicleStatus.ACTIVE);
        Long residentId = createResident("AHR10 Inactive Guard " + token, "AHR10-IG-" + token);
        Long authorizedId = createResident("AHR10 Inactive Guard Grantee " + token, "AHR10-IGG-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(5).withNano(0);
        Long membershipId = createMembership(apartmentId, residentId, MembershipRole.MEMBER, validFrom);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(residentId, validFrom, null, "Owner fixture"), null);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        residentId, null, validFrom, null, "Guarantor fixture"), null);

        try {
            assertThrows(ResidentStatusConflictException.class, () -> residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Deactivation", null, null), null));
            assertEquals(ResidentStatus.ACTIVE, residentService.detail(residentId, null).status());
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, membershipId));
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(owner.id(), null).status());

            LocalDateTime effectiveAt = LocalDateTime.now().minusSeconds(2).withNano(0);
            ResidentDetail inactive = residentStatusService.changeStatus(residentId,
                    new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Deactivation",
                            List.of(new ResidentStatusChangeRequest.MembershipAction(
                                    membershipId, RelationLifecycleAction.END, effectiveAt, "Membership ended")),
                            List.of(
                                    new ResidentStatusChangeRequest.VehicleRightAction(
                                            owner.id(), RelationLifecycleAction.END, effectiveAt, "Owner ended"),
                                    new ResidentStatusChangeRequest.VehicleRightAction(
                                            grant.id(), RelationLifecycleAction.REVOKE, effectiveAt, "Grant revoked"))), null);

            assertEquals(ResidentStatus.INACTIVE, inactive.status());
            assertEquals("INACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, membershipId));
            assertEquals(effectiveAt, jdbcTemplate.queryForObject(
                    "SELECT valid_to FROM apartment_memberships WHERE id = ?", LocalDateTime.class, membershipId));
            assertEquals(VehicleRelationStatus.INACTIVE, authorizedUserService.detail(owner.id(), null).status());
            assertEquals(effectiveAt, authorizedUserService.detail(owner.id(), null).validTo());
            assertEquals(VehicleRelationStatus.REVOKED, authorizedUserService.detail(grant.id(), null).status());
            assertEquals(effectiveAt, authorizedUserService.detail(grant.id(), null).validTo());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' AND entity_id = ? "
                            + "AND action = 'MEMBERSHIP_ENDED'", Integer.class, membershipId.toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_ENDED'",
                    Integer.class, owner.id().toString()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_REVOKED'",
                    Integer.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {residentId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void residentStatusAuditFailureRollsBackGuarantorInvalidationAcrossModules() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "STATUS-ROLLBACK-" + token, "SR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR10 Rollback Owner " + token, "AHR10-SRO-" + token);
        Long authorizedId = createResident("AHR10 Rollback User " + token, "AHR10-SRU-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(5).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(2), null, "Owner fixture"), null);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, null, "Grant fixture"), null);

        try {
            org.mockito.Mockito.doAnswer(invocation -> {
                if ("RESIDENT_STATUS_CHANGED".equals(invocation.getArgument(0))) {
                    throw new IllegalStateException("Synthetic status audit failure");
                }
                return invocation.callRealMethod();
            }).when(auditService).record(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.nullable(User.class),
                    org.mockito.ArgumentMatchers.nullable(String.class),
                    org.mockito.ArgumentMatchers.nullable(String.class));

            assertThrows(IllegalStateException.class, () -> residentStatusService.changeStatus(ownerId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Rollback block", null, null), null));

            assertEquals(ResidentStatus.ACTIVE, residentService.detail(ownerId, null).status());
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(owner.id(), null).status());
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(grant.id(), null).status());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ? "
                            + "AND action = 'RESIDENT_STATUS_CHANGED'",
                    Integer.class, ownerId.toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action LIKE 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_%'",
                    Integer.class, grant.id().toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
        }
    }

    @Test
    void residentBlockingRacingOwnerGuarantorGrantLeavesNoEffectiveGrant() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "STATUS-RACE-" + token, "SRACE-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR10 Race Owner " + token, "AHR10-RACE-O-" + token);
        Long authorizedId = createResident("AHR10 Race User " + token, "AHR10-RACE-U-" + token);
        LocalDateTime ownerFrom = LocalDateTime.now().minusDays(10).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, ownerFrom, null, "Owner fixture"), null);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ResidentDetail> statusChange = executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Resident status race did not start");
            }
            return residentStatusService.changeStatus(ownerId,
                    new ResidentStatusChangeRequest(ResidentStatus.BLOCKED, "Concurrent block", null, null), null);
        });
        Future<VehicleRightDetail> grant = executor.submit(() -> concurrentGrant(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null,
                        LocalDateTime.now().minusDays(1).withNano(0), null, "Concurrent grant"), ready, start));

        VehicleRightDetail createdGrant = null;
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            ResidentDetail blocked = statusChange.get(15, TimeUnit.SECONDS);
            assertEquals(ResidentStatus.BLOCKED, blocked.status());
            try {
                createdGrant = grant.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof VehicleStatusConflictException)) {
                    throw exception;
                }
            }

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ? "
                            + "AND resident_id = ? AND relation_type = 'AUTHORIZED_USER' AND status = 'ACTIVE' "
                            + "AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, vehicle.vehicleId(), authorizedId, blocked.updatedAt()));
            if (createdGrant != null) {
                VehicleRightDetail finalGrant = authorizedUserService.detail(createdGrant.id(), null);
                assertEquals(VehicleRelationStatus.INACTIVE, finalGrant.status());
                assertEquals(blocked.updatedAt(), finalGrant.validTo());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
        }
    }

    @Test
    void voidAuthorizedUserFromActiveEndedAndRevokedStatesPreservesIntervalsAndAudits() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-RIGHT-" + token, "VR-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR11 VOID Owner " + token, "AHR11-VO-" + token);
        Long activeUserId = createResident("AHR11 VOID Active User " + token, "AHR11-VA-" + token);
        Long endedUserId = createResident("AHR11 VOID Ended User " + token, "AHR11-VE-" + token);
        Long revokedUserId = createResident("AHR11 VOID Revoked User " + token, "AHR11-VR-" + token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(10).withNano(0);
        LocalDateTime validTo = LocalDateTime.now().plusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom.minusDays(5), null, "Owner fixture"), null);
        VehicleRightDetail active = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), activeUserId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, validTo, "Active fixture"), null);
        VehicleRightDetail ended = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), endedUserId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, validTo, "Ended fixture"), null);
        VehicleRightDetail revoked = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), revokedUserId, VehicleRelationGuarantorType.OWNER,
                        ownerId, null, validFrom, validTo, "Revoked fixture"), null);
        LocalDateTime endAt = LocalDateTime.now().minusSeconds(3).withNano(0);
        LocalDateTime revokeAt = LocalDateTime.now().minusSeconds(2).withNano(0);
        vehicleRightLifecycleService.end(ended.id(),
                new VehicleRightLifecycleRequest(endAt, "Ended before VOID"), null);
        vehicleRightLifecycleService.revoke(revoked.id(),
                new VehicleRightLifecycleRequest(revokeAt, "Revoked before VOID"), null);

        try {
            VehicleRightDetail voidedActive = vehicleRightLifecycleService.voidRight(
                    active.id(), new VehicleRightVoidRequest("Active record created in error"), null);
            VehicleRightDetail voidedEnded = vehicleRightLifecycleService.voidRight(
                    ended.id(), new VehicleRightVoidRequest("Ended record created in error"), null);
            VehicleRightDetail voidedRevoked = vehicleRightLifecycleService.voidRight(
                    revoked.id(), new VehicleRightVoidRequest("Revoked record created in error"), null);

            assertEquals(VehicleRelationStatus.VOID, voidedActive.status());
            assertEquals(validFrom, voidedActive.validFrom());
            assertEquals(validTo, voidedActive.validTo());
            assertEquals("Active record created in error", voidedActive.lifecycleReason());
            assertEquals(VehicleRelationStatus.VOID, voidedEnded.status());
            assertEquals(endAt, voidedEnded.validTo());
            assertEquals(VehicleRelationStatus.VOID, voidedRevoked.status());
            assertEquals(revokeAt, voidedRevoked.validTo());
            for (VehicleRightDetail voided : List.of(voidedActive, voidedEnded, voidedRevoked)) {
                assertTrue(voided.lifecycleChangedAt() != null);
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                                + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                        Integer.class, voided.id().toString()));
            }
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.voidRight(
                    active.id(), new VehicleRightVoidRequest("Repeated VOID"), null));
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {ownerId, activeUserId, endedUserId, revokedUserId}, new Long[0]);
        }
    }

    @Test
    void voidHouseholdHeadMembershipVoidsProvableActiveAndTerminalGrantHistory() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-MEMBER-" + token, "VM-" + token,
                VehicleStatus.ACTIVE);
        Long previousOwnerId = createResident("AHR11 VOID Previous Owner " + token, "AHR11-VPO-" + token);
        Long currentOwnerId = createResident("AHR11 VOID Current Owner " + token, "AHR11-VCO-" + token);
        Long headId = createResident("AHR11 VOID Household Head " + token, "AHR11-VHH-" + token);
        Long activeUserId = createResident("AHR11 VOID Active Grant " + token, "AHR11-VGA-" + token);
        Long endedUserId = createResident("AHR11 VOID Ended Grant " + token, "AHR11-VGE-" + token);
        Long revokedUserId = createResident("AHR11 VOID Revoked Grant " + token, "AHR11-VGR-" + token);
        Long scheduledUserId = createResident("AHR11 VOID Scheduled Grant " + token, "AHR11-VGS-" + token);
        Long membershipCancelledUserId = createResident(
                "AHR11 VOID Membership Cancelled Grant " + token, "AHR11-VMCG-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime base = LocalDateTime.now().minusDays(30).withNano(0);
        LocalDateTime transferAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        VehicleRightDetail previousOwner = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(previousOwnerId, base, null, "Previous owner fixture"), null);
        createMembership(apartmentId, previousOwnerId, MembershipRole.MEMBER, base);
        createMembership(apartmentId, currentOwnerId, MembershipRole.MEMBER, base);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, base);
        VehicleRightDetail previousOwnerGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), scheduledUserId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        transferAt.plusDays(5), null, "Pre-transfer scheduled grant"), null);
        vehicleService.transferOwner(vehicle.vehicleId(), new VehicleOwnerTransferRequest(
                previousOwner.id(), currentOwnerId, transferAt, "Owner transfer fixture"), null);

        LocalDateTime grantFrom = transferAt.plusSeconds(1);
        VehicleRightDetail activeGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), activeUserId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        grantFrom, null, "Active grant fixture"), null);
        VehicleRightDetail endedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), endedUserId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        grantFrom, null, "Ended grant fixture"), null);
        VehicleRightDetail revokedGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), revokedUserId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        grantFrom, null, "Revoked grant fixture"), null);
        LocalDateTime membershipScheduledFrom = LocalDateTime.now().plusDays(25).withNano(0);
        VehicleRightDetail membershipCancelledGrant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), membershipCancelledUserId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        membershipScheduledFrom, null, "Membership-cancelled grant fixture"), null);
        LocalDateTime endAt = LocalDateTime.now().minusSeconds(3).withNano(0);
        LocalDateTime revokeAt = LocalDateTime.now().minusSeconds(2).withNano(0);
        vehicleRightLifecycleService.end(endedGrant.id(),
                new VehicleRightLifecycleRequest(endAt, "Ended before membership VOID"), null);
        vehicleRightLifecycleService.revoke(revokedGrant.id(),
                new VehicleRightLifecycleRequest(revokeAt, "Revoked before membership VOID"), null);
        LocalDateTime membershipLossAt = LocalDateTime.now().minusSeconds(1).withNano(0);
        membershipService.end(headMembershipId,
                new MembershipLifecycleRequest(membershipLossAt, "Membership authority ended"), null);

        try {
            VehicleRightDetail precancelled = authorizedUserService.detail(previousOwnerGrant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, precancelled.status());
            assertNull(precancelled.validTo());
            VehicleRightDetail cancelledByMembership = authorizedUserService.detail(membershipCancelledGrant.id(), null);
            assertEquals(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED, cancelledByMembership.status());
            assertNull(cancelledByMembership.validTo());
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.voidRight(
                    previousOwnerGrant.id(), new VehicleRightVoidRequest("Direct pre-effective VOID"), null));
            assertNull(authorizedUserService.detail(previousOwnerGrant.id(), null).validTo());

            MembershipDetail voidedHead = membershipService.voidMembership(
                    headMembershipId, new MembershipVoidRequest("Household head created in error"), null);

            assertEquals(MembershipStatus.VOID, voidedHead.status());
            assertEquals(base, voidedHead.validFrom());
            assertEquals(membershipLossAt, voidedHead.validTo());
            for (VehicleRightDetail original : List.of(
                    previousOwnerGrant, membershipCancelledGrant, activeGrant, endedGrant, revokedGrant)) {
                VehicleRightDetail voidedGrant = authorizedUserService.detail(original.id(), null);
                assertEquals(VehicleRelationStatus.VOID, voidedGrant.status());
                assertEquals(original.validFrom(), voidedGrant.validFrom());
                assertTrue(voidedGrant.lifecycleChangedAt() != null);
                assertEquals("Household head created in error", voidedGrant.lifecycleReason());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                                + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                        Integer.class, original.id().toString()));
            }
            assertNull(authorizedUserService.detail(previousOwnerGrant.id(), null).validTo());
            assertNull(authorizedUserService.detail(membershipCancelledGrant.id(), null).validTo());
            assertEquals(membershipLossAt, authorizedUserService.detail(activeGrant.id(), null).validTo());
            assertEquals(endAt, authorizedUserService.detail(endedGrant.id(), null).validTo());
            assertEquals(revokeAt, authorizedUserService.detail(revokedGrant.id(), null).validTo());
        } finally {
            cleanupGrantFixtures(vehicle,
                    new Long[] {previousOwnerId, currentOwnerId, headId, activeUserId, endedUserId,
                            revokedUserId, scheduledUserId, membershipCancelledUserId},
                    new Long[] {apartmentId});
        }
    }

    @Test
    void voidOwnerMembershipVoidsHouseholdHeadGuarantorGrant() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-OWNER-MEMBER-" + token, "VOM-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR11 VOID Membership Owner " + token, "AHR11-VMO-" + token);
        Long headId = createResident("AHR11 VOID Membership Head " + token, "AHR11-VMH-" + token);
        Long authorizedId = createResident("AHR11 VOID Membership User " + token, "AHR11-VMU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        Long ownerMembershipId = createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        validFrom.plusDays(1), null, "Grant fixture"), null);

        try {
            MembershipDetail voidedMembership = membershipService.voidMembership(
                    ownerMembershipId, new MembershipVoidRequest("Owner Membership created in error"), null);
            VehicleRightDetail voidedGrant = authorizedUserService.detail(grant.id(), null);

            assertEquals(MembershipStatus.VOID, voidedMembership.status());
            assertEquals(VehicleRelationStatus.VOID, voidedGrant.status());
            assertEquals("Owner Membership created in error", voidedGrant.lifecycleReason());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                    Integer.class, grant.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void voidOwnerVoidsAllProvableDirectAndHouseholdGrantHistory() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-OWNER-" + token, "VO-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR11 VOID Owner History " + token, "AHR11-VOH-" + token);
        Long headId = createResident("AHR11 VOID Owner Head " + token, "AHR11-VOHH-" + token);
        Long directActiveId = createResident("AHR11 VOID Direct Active " + token, "AHR11-VDA-" + token);
        Long directEndedId = createResident("AHR11 VOID Direct Ended " + token, "AHR11-VDE-" + token);
        Long directRevokedId = createResident("AHR11 VOID Direct Revoked " + token, "AHR11-VDR-" + token);
        Long directScheduledId = createResident("AHR11 VOID Direct Scheduled " + token, "AHR11-VDS-" + token);
        Long householdActiveId = createResident("AHR11 VOID Household Active " + token, "AHR11-VHA-" + token);
        Long householdScheduledId = createResident("AHR11 VOID Household Scheduled " + token, "AHR11-VHS-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime ownerFrom = LocalDateTime.now().minusDays(30).withNano(0);
        VehicleRightDetail owner = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, ownerFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, ownerFrom);
        createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, ownerFrom);

        LocalDateTime grantFrom = ownerFrom.plusDays(1);
        LocalDateTime scheduledFrom = LocalDateTime.now().plusDays(20).withNano(0);
        VehicleRightDetail directActive = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), directActiveId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, grantFrom, null, "Direct fixture"), null);
        VehicleRightDetail directEnded = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), directEndedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, grantFrom, null, "Ended fixture"), null);
        VehicleRightDetail directRevoked = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), directRevokedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, grantFrom, null, "Revoked fixture"), null);
        VehicleRightDetail directScheduled = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), directScheduledId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, scheduledFrom, null,
                        "Scheduled direct fixture"), null);
        VehicleRightDetail householdActive = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), householdActiveId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        grantFrom, null, "Household fixture"), null);
        VehicleRightDetail householdScheduled = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), householdScheduledId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        scheduledFrom, null, "Scheduled household fixture"), null);

        LocalDateTime endAt = LocalDateTime.now().minusSeconds(6).withNano(0);
        LocalDateTime revokeAt = LocalDateTime.now().minusSeconds(5).withNano(0);
        LocalDateTime ownerLossAt = LocalDateTime.now().minusSeconds(3).withNano(0);
        vehicleRightLifecycleService.end(directEnded.id(),
                new VehicleRightLifecycleRequest(endAt, "Ended before owner loss"), null);
        vehicleRightLifecycleService.revoke(directRevoked.id(),
                new VehicleRightLifecycleRequest(revokeAt, "Revoked before owner loss"), null);
        vehicleRightLifecycleService.end(owner.id(),
                new VehicleRightLifecycleRequest(ownerLossAt, "Owner authority ended"), null);

        try {
            assertThrows(VehicleRelationStateConflictException.class, () -> vehicleRightLifecycleService.voidRight(
                    directScheduled.id(), new VehicleRightVoidRequest("Direct pre-effective VOID"), null));
            VehicleRightDetail voidedOwner = vehicleRightLifecycleService.voidRight(
                    owner.id(), new VehicleRightVoidRequest("Owner relation created in error"), null);

            assertEquals(VehicleRelationStatus.VOID, voidedOwner.status());
            assertEquals(ownerLossAt, voidedOwner.validTo());
            assertEquals("Owner relation created in error", voidedOwner.lifecycleReason());
            for (VehicleRightDetail original : List.of(directActive, directEnded, directRevoked,
                    directScheduled, householdActive, householdScheduled)) {
                VehicleRightDetail voided = authorizedUserService.detail(original.id(), null);
                assertEquals(VehicleRelationStatus.VOID, voided.status());
                assertEquals(original.validFrom(), voided.validFrom());
                assertEquals("Owner relation created in error", voided.lifecycleReason());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                                + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                        Integer.class, original.id().toString()));
            }
            assertEquals(ownerLossAt, authorizedUserService.detail(directActive.id(), null).validTo());
            assertEquals(endAt, authorizedUserService.detail(directEnded.id(), null).validTo());
            assertEquals(revokeAt, authorizedUserService.detail(directRevoked.id(), null).validTo());
            assertNull(authorizedUserService.detail(directScheduled.id(), null).validTo());
            assertEquals(ownerLossAt, authorizedUserService.detail(householdActive.id(), null).validTo());
            assertNull(authorizedUserService.detail(householdScheduled.id(), null).validTo());
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, directActiveId, directEndedId,
                    directRevokedId, directScheduledId, householdActiveId, householdScheduledId},
                    new Long[] {apartmentId});
        }
    }

    @Test
    void membershipVoidAuditFailureRollsBackDependentVehicleRightVoid() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-ROLLBACK-" + token, "VRB-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR11 VOID Rollback Owner " + token, "AHR11-VRO-" + token);
        Long headId = createResident("AHR11 VOID Rollback Head " + token, "AHR11-VRH-" + token);
        Long authorizedId = createResident("AHR11 VOID Rollback User " + token, "AHR11-VRU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        validFrom, null, "Grant fixture"), null);

        try {
            doAnswer(invocation -> {
                if ("MEMBERSHIP_VOIDED".equals(invocation.getArgument(0))
                        && headMembershipId.toString().equals(invocation.getArgument(2))) {
                    throw new IllegalStateException("Synthetic Membership VOID audit failure");
                }
                return invocation.callRealMethod();
            }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                    nullable(String.class), nullable(String.class));

            assertThrows(IllegalStateException.class, () -> membershipService.voidMembership(
                    headMembershipId, new MembershipVoidRequest("Head created in error"), null));

            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, headMembershipId));
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(grant.id(), null).status());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                    Integer.class, grant.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'MEMBERSHIP_VOIDED'",
                    Integer.class, headMembershipId.toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void membershipVoidAuditFailureRollsBackDependentVehicleRightVoids() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "VOID-ROLLBACK-" + token, "VRB-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHR11 VOID Rollback Owner " + token, "AHR11-VRO-" + token);
        Long headId = createResident("AHR11 VOID Rollback Head " + token, "AHR11-VRH-" + token);
        Long authorizedId = createResident("AHR11 VOID Rollback User " + token, "AHR11-VRU-" + token);
        Long apartmentId = createApartment(token);
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        createMembership(apartmentId, ownerId, MembershipRole.MEMBER, validFrom);
        Long headMembershipId = createMembership(apartmentId, headId, MembershipRole.HOUSEHOLD_HEAD, validFrom);
        VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, headId, apartmentId,
                        validFrom, null, "Grant fixture"), null);

        try {
            doAnswer(invocation -> {
                if ("MEMBERSHIP_VOIDED".equals(invocation.getArgument(0))
                        && headMembershipId.toString().equals(invocation.getArgument(2))) {
                    throw new IllegalStateException("Synthetic Membership VOID audit failure");
                }
                return invocation.callRealMethod();
            }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                    nullable(String.class), nullable(String.class));

            assertThrows(IllegalStateException.class, () -> membershipService.voidMembership(
                    headMembershipId, new MembershipVoidRequest("Head created in error"), null));

            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM apartment_memberships WHERE id = ?", String.class, headMembershipId));
            assertEquals(VehicleRelationStatus.ACTIVE, authorizedUserService.detail(grant.id(), null).status());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_RIGHT_VOIDED'",
                    Integer.class, grant.id().toString()));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                            + "AND entity_id = ? AND action = 'MEMBERSHIP_VOIDED'",
                    Integer.class, headMembershipId.toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, headId, authorizedId}, new Long[] {apartmentId});
        }
    }

    @Test
    void duePendingTransitionEndsAnEffectiveGrantAtItsEffectiveTimeAndProcessesOnlyOnce() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "DUE-TRANSITION-" + token, "DT-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR03 Pending Owner " + token, "AHRR03-PO-" + token);
        Long authorizedId = createResident("AHRR03 Pending User " + token, "AHRR03-PU-" + token);
        User actor = createActor("AHRR03-PENDING-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        VehicleRightDetail grant = vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        VehicleRightDetail authorized = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom.plusDays(1), null,
                        "Grant fixture"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        Long pendingId = new TransactionTemplate(transactionManager).execute(status -> {
            VehicleResidentRelation relation = entityManager.find(VehicleResidentRelation.class, authorized.id());
            relation.setValidTo(effectiveAt);
            VehicleRightPendingTransition pending = new VehicleRightPendingTransition();
            pending.setVehicleRight(relation);
            pending.setEffectiveAt(effectiveAt);
            pending.setReason("Owner transfer scheduled");
            pending.setSourceActor(entityManager.getReference(User.class, actorId));
            entityManager.persist(pending);
            entityManager.flush();
            return pending.getId();
        });

        try {
            LocalDateTime processingTime = LocalDateTime.now().withNano(0);
            assertFalse(pendingTransitionProcessor.processPendingTransition(
                    pendingId, effectiveAt.minusSeconds(1)));
            assertEquals(1, pendingTransitionRepository.count());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'ACTIVE' "
                            + "AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)",
                    Integer.class, authorized.id(), processingTime, processingTime));

            doAnswer(invocation -> {
                if ("VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED".equals(invocation.getArgument(0))) {
                    throw new IllegalStateException("Synthetic pending transition audit failure");
                }
                return invocation.callRealMethod();
            }).when(auditService).record(anyString(), anyString(), anyString(), nullable(User.class),
                    nullable(String.class), nullable(String.class));
            assertThrows(IllegalStateException.class,
                    () -> pendingTransitionProcessor.processPendingTransition(pendingId, effectiveAt));
            assertEquals(1, pendingTransitionRepository.count());
            assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                    "SELECT status FROM vehicle_resident_relations WHERE id = ?", String.class, authorized.id()));
            org.mockito.Mockito.reset(auditService);

            assertTrue(pendingTransitionProcessor.processPendingTransition(pendingId, processingTime));

            VehicleRightDetail transitioned = authorizedUserService.detail(authorized.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, transitioned.status());
            assertEquals(effectiveAt, transitioned.validTo());
            assertEquals(effectiveAt, transitioned.lifecycleChangedAt());
            assertEquals("Owner transfer scheduled", transitioned.lifecycleReason());
            assertEquals(actorId, jdbcTemplate.queryForObject(
                    "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Long.class, authorized.id().toString()));
            assertEquals(0, pendingTransitionRepository.count());
            assertFalse(pendingTransitionProcessor.processPendingTransition(pendingId, processingTime));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Integer.class, authorized.id().toString()));
        } finally {
            org.mockito.Mockito.reset(auditService);
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    @Test
    void duePendingTransitionDoesNotOverwriteAnExplicitLifecycleCommand() {
        String token = UUID.randomUUID().toString().substring(0, 10);
        VehicleFixture vehicle = createVehicle(token, "DUE-CONFLICT-" + token, "DC-" + token,
                VehicleStatus.ACTIVE);
        Long ownerId = createResident("AHRR03 Conflict Owner " + token, "AHRR03-CO-" + token);
        Long authorizedId = createResident("AHRR03 Conflict User " + token, "AHRR03-CU-" + token);
        User actor = createActor("AHRR03-CONFLICT-ACTOR-" + token);
        Long actorId = actor.getId();
        LocalDateTime validFrom = LocalDateTime.now().minusDays(30).withNano(0);
        vehicleService.assignOwner(vehicle.vehicleId(),
                new VehicleOwnerAssignmentRequest(ownerId, validFrom, null, "Owner fixture"), null);
        VehicleRightDetail authorized = authorizedUserService.grantAuthorizedUser(vehicle.vehicleId(),
                new AuthorizedUserGrantRequest(vehicle.vehicleId(), authorizedId,
                        VehicleRelationGuarantorType.OWNER, ownerId, null, validFrom.plusDays(1), null,
                        "Grant fixture"), null);
        LocalDateTime effectiveAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        Long pendingId = new TransactionTemplate(transactionManager).execute(status -> {
            VehicleResidentRelation relation = entityManager.find(VehicleResidentRelation.class, authorized.id());
            relation.setValidTo(effectiveAt);
            VehicleRightPendingTransition pending = new VehicleRightPendingTransition();
            pending.setVehicleRight(relation);
            pending.setEffectiveAt(effectiveAt);
            pending.setReason("Scheduled authority loss");
            pending.setSourceActor(entityManager.getReference(User.class, actorId));
            entityManager.persist(pending);
            entityManager.flush();
            return pending.getId();
        });

        try {
            vehicleRightLifecycleService.end(authorized.id(),
                    new VehicleRightLifecycleRequest(effectiveAt, "Explicitly ended"), null);

            assertFalse(pendingTransitionProcessor.processPendingTransition(pendingId, LocalDateTime.now()));

            VehicleRightDetail ended = authorizedUserService.detail(authorized.id(), null);
            assertEquals(VehicleRelationStatus.INACTIVE, ended.status());
            assertEquals("Explicitly ended", ended.lifecycleReason());
            assertEquals(0, pendingTransitionRepository.count());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                            + "AND entity_id = ? AND action = 'VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED'",
                    Integer.class, authorized.id().toString()));
        } finally {
            cleanupGrantFixtures(vehicle, new Long[] {ownerId, authorizedId}, new Long[0]);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
    }

    private VehicleFixture createVehicle(
            String token, String plateNumber, String plateNormalized, VehicleStatus status) {
        return new TransactionTemplate(transactionManager).execute(transactionStatus -> {
            VehicleFamily family = new VehicleFamily();
            family.setCode("AHR07-FAMILY-" + token);
            family.setName("AHR-07 fixture " + token);
            entityManager.persist(family);

            VehicleCategory category = new VehicleCategory();
            category.setFamily(family);
            category.setCode("AHR07-CATEGORY-" + token);
            category.setName("AHR-07 fixture " + token);
            entityManager.persist(category);

            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleCategory(category);
            vehicle.setPlateNumber(plateNumber);
            vehicle.setPlateNormalized(plateNormalized);
            vehicle.setStatus(status);
            vehicle.setCreatedAt(LocalDateTime.now());
            vehicle.setUpdatedAt(LocalDateTime.now());
            entityManager.persist(vehicle);
            entityManager.flush();
            return new VehicleFixture(vehicle.getId(), category.getId(), family.getId());
        });
    }

    private Long createResident(String fullName, String identityNumber) {
        return residentService.createOrReuse(
                new ResidentCreateRequest(fullName, identityNumber, null, null, null), null).resident().id();
    }

    private User createActor(String username) {
        return new TransactionTemplate(transactionManager).execute(transactionStatus -> {
            User actor = new User();
            actor.setUsername(username);
            actor.setFullName("AHR-08 test actor");
            actor.setStatus(UserStatus.ACTIVE);
            entityManager.persist(actor);
            entityManager.flush();
            return actor;
        });
    }

    private Long createApartment(String token) {
        return apartmentService.create(
                new ApartmentCreateRequest("AHR08 Apartment " + token, "A-01", null), null).id();
    }

    private Long createMembership(Long apartmentId, Long residentId, MembershipRole role, LocalDateTime validFrom) {
        return createMembership(apartmentId, residentId, role, validFrom, null);
    }

    private Long createMembership(
            Long apartmentId, Long residentId, MembershipRole role, LocalDateTime validFrom, LocalDateTime validTo) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Apartment apartment = entityManager.getReference(Apartment.class, apartmentId);
            Resident resident = entityManager.getReference(Resident.class, residentId);
            ApartmentMembership membership = new ApartmentMembership();
            membership.setApartment(apartment);
            membership.setResident(resident);
            membership.setMemberRole(role);
            membership.setValidFrom(validFrom);
            membership.setValidTo(validTo);
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setCreatedAt(LocalDateTime.now());
            entityManager.persist(membership);
            entityManager.flush();
            return membership.getId();
        });
    }

    private VehicleRightDetail concurrentAssignment(
            Long vehicleId,
            VehicleOwnerAssignmentRequest request,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent OWNER assignment did not start");
        }
        return vehicleService.assignOwner(vehicleId, request, null);
    }

    private VehicleRightDetail concurrentGrant(
            Long vehicleId,
            AuthorizedUserGrantRequest request,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent authorized-user grant did not start");
        }
        return authorizedUserService.grantAuthorizedUser(vehicleId, request, null);
    }

    private VehicleRightDetail concurrentOwnerTransfer(
            Long vehicleId,
            VehicleOwnerTransferRequest request,
            User actor,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent OWNER transfer did not start");
        }
        return vehicleService.transferOwner(vehicleId, request, actor);
    }

    private MembershipDetail concurrentHouseholdHeadTransfer(
            Long apartmentId,
            MembershipTransferRequest request,
            User actor,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent household-head transfer did not start");
        }
        return membershipService.transferHouseholdHead(apartmentId, request, actor);
    }

    private void cleanupFixtures(VehicleFixture vehicle, Long... residentIds) {
        cleanupGrantFixtures(vehicle, residentIds, new Long[0]);
    }

    private void cleanupGrantFixtures(VehicleFixture vehicle, Long[] residentIds, Long[] apartmentIds) {
        jdbcTemplate.update("DELETE pending FROM vehicle_right_pending_transitions pending "
                + "JOIN vehicle_resident_relations relation ON relation.id = pending.vehicle_right_id "
                + "WHERE relation.vehicle_id = ?", vehicle.vehicleId());
        List<String> relationIds = jdbcTemplate.queryForList(
                "SELECT CAST(id AS CHAR) FROM vehicle_resident_relations WHERE vehicle_id = ?",
                String.class, vehicle.vehicleId());
        for (String relationId : relationIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                    + "AND entity_id = ?", relationId);
        }
        jdbcTemplate.update("DELETE FROM vehicle_resident_relations WHERE vehicle_id = ?", vehicle.vehicleId());
        jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'VEHICLE' AND entity_id = ?",
                vehicle.vehicleId().toString());
        jdbcTemplate.update("DELETE FROM vehicles WHERE id = ?", vehicle.vehicleId());
        jdbcTemplate.update("DELETE FROM vehicle_categories WHERE id = ?", vehicle.categoryId());
        jdbcTemplate.update("DELETE FROM vehicle_families WHERE id = ?", vehicle.familyId());
        for (Long apartmentId : apartmentIds) {
            List<String> membershipIds = jdbcTemplate.queryForList(
                    "SELECT CAST(id AS CHAR) FROM apartment_memberships WHERE apartment_id = ?",
                    String.class, apartmentId);
            for (String membershipId : membershipIds) {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'APARTMENT_MEMBERSHIP' "
                        + "AND entity_id = ?", membershipId);
            }
            jdbcTemplate.update("DELETE FROM apartment_memberships WHERE apartment_id = ?", apartmentId);
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ?",
                    apartmentId.toString());
            jdbcTemplate.update("DELETE FROM apartments WHERE id = ?", apartmentId);
        }
        for (Long residentId : residentIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'RESIDENT' AND entity_id = ?",
                    residentId.toString());
            jdbcTemplate.update("DELETE FROM residents WHERE id = ?", residentId);
        }
    }

    private int grantAuditCount(Long vehicleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'VEHICLE_RESIDENT_RELATION' "
                        + "AND action = 'VEHICLE_AUTHORIZED_USER_GRANTED' "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(new_data, '$.vehicle_id')) = ?",
                Integer.class, vehicleId.toString());
    }

    private record VehicleFixture(Long vehicleId, Long categoryId, Long familyId) {}
}
