package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleRightPendingTransition;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleStatus;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRightPendingTransitionRepository;

@SpringBootTest
@ActiveProfiles("test")
class ApartmentResidentSchemaIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private VehicleRightPendingTransitionRepository pendingTransitionRepository;

    @Test
    void migrationAddsNormalizedKeysAndOwnedLifecycleSchemaWithoutChangingCardStatus() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1", Integer.class));
        assertColumn("apartments", "building_key", "varbinary(2048)", "NO");
        assertColumn("apartments", "apartment_code_key", "varbinary(768)", "NO");
        assertColumn("residents", "identity_number_key", "varbinary(512)", "NO");
        assertColumn("apartment_memberships", "lifecycle_changed_at", "datetime(6)", "YES");
        assertColumn("apartment_memberships", "lifecycle_reason", "varchar(500)", "YES");
        assertColumn("vehicle_resident_relations", "guarantor_type", "enum('owner','household_head')", "YES");
        assertColumn("vehicle_resident_relations", "lifecycle_changed_at", "datetime(6)", "YES");
        assertColumn("vehicle_resident_relations", "lifecycle_reason", "varchar(500)", "YES");

        assertEquals(2, uniqueIndexColumnCount("apartments", "uk_apartments_normalized_identity"));
        assertEquals(1, uniqueIndexColumnCount("residents", "uk_residents_identity_number_key"));
        assertEquals(1, columnCount("vehicle_resident_relations", "guarantor_resident_id"));
        assertEquals(1, columnCount("vehicle_resident_relations", "guarantor_apartment_id"));
        assertEquals(0, columnCount("apartment_memberships", "cancelled_at"));
        assertEquals(0, columnCount("vehicle_resident_relations", "cancelled_at"));
        assertTrue(columnType("apartment_memberships", "status").contains("VOID"));
        assertTrue(columnType("vehicle_resident_relations", "status").contains("PRE_EFFECTIVE_CANCELLED"));
        assertEquals("enum('active','inactive','revoked')", columnType("card_assignments", "status").toLowerCase());

        assertEquals("RESTRICT", foreignKeyDeleteRule("fk_vehicle_relation_guarantor_resident"));
        assertEquals("RESTRICT", foreignKeyDeleteRule("fk_vehicle_relation_guarantor_apartment"));
        assertTrue(checkConstraintExists("ck_apartment_membership_lifecycle"));
        assertTrue(checkConstraintExists("ck_vehicle_relation_lifecycle"));
        assertTrue(checkConstraintExists("ck_vehicle_relation_guarantor"));
    }

    @Test
    @Transactional
    void apartmentJpaSettersPersistCanonicalBinaryKeysAndUniqueIndexRejectsEquivalentIdentity() {
        Apartment apartment = apartment("  Cafe\u0301\t Wing ", "  A- 01 ");
        entityManager.persist(apartment);
        entityManager.flush();

        assertArrayEquals(ResidentIdentityKeyNormalizer.buildingKey(apartment.getBuilding()),
                jdbcTemplate.queryForObject(
                        "SELECT building_key FROM apartments WHERE id = ?", byte[].class, apartment.getId()));
        assertArrayEquals(ResidentIdentityKeyNormalizer.apartmentCodeKey(apartment.getApartmentCode()),
                jdbcTemplate.queryForObject(
                        "SELECT apartment_code_key FROM apartments WHERE id = ?", byte[].class, apartment.getId()));
        assertEquals(apartment.getId(), apartmentRepository
                .findByCanonicalBusinessIdentity("CAFÉ\tWING", "a- 01").orElseThrow().getId());

        byte[] buildingKey = ResidentIdentityKeyNormalizer.buildingKey("Café Wing");
        byte[] apartmentCodeKey = ResidentIdentityKeyNormalizer.apartmentCodeKey("a- 01");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO apartments (building, apartment_code, building_key, apartment_code_key, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                "CAFÉ WING", "a- 01", buildingKey, apartmentCodeKey));
    }

    @Test
    @Transactional
    void residentJpaSetterPersistsCanonicalIdentityKeyAndUniqueIndexRejectsEquivalentIdentity() {
        Resident resident = resident("Preflight Resident", " ab\u2003ç-α１２ ");
        entityManager.persist(resident);
        entityManager.flush();

        assertArrayEquals(ResidentIdentityKeyNormalizer.identityNumberKey(resident.getIdentityNumber()),
                jdbcTemplate.queryForObject(
                        "SELECT identity_number_key FROM residents WHERE id = ?", byte[].class, resident.getId()));
        assertEquals(resident.getId(), residentRepository
                .findByCanonicalIdentityNumber("\tABÇ -α１２\u00a0").orElseThrow().getId());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO residents (full_name, identity_number, identity_number_key, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                "Equivalent Identity Fixture", "ABÇ-α１２",
                ResidentIdentityKeyNormalizer.identityNumberKey("ABÇ-α１２")));
    }

    @Test
    @Transactional
    void membershipJpaMappingAllowsActiveAndValidEndWithSeparateLifecycleTime() {
        Apartment apartment = apartment("Membership Fixture Building", "M-01");
        Resident resident = resident("Membership Fixture Resident", "MEMBER-01");
        entityManager.persist(apartment);
        entityManager.persist(resident);
        entityManager.flush();

        ApartmentMembership active = new ApartmentMembership();
        active.setApartment(apartment);
        active.setResident(resident);
        active.setMemberRole(MembershipRole.MEMBER);
        active.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        active.setStatus(MembershipStatus.ACTIVE);
        entityManager.persist(active);
        entityManager.flush();

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM apartment_memberships "
                        + "WHERE id = ? AND lifecycle_changed_at IS NULL AND lifecycle_reason IS NULL",
                Integer.class, active.getId()));

        jdbcTemplate.update(
                "INSERT INTO apartment_memberships "
                        + "(apartment_id, resident_id, member_role, valid_from, valid_to, status, "
                        + "lifecycle_changed_at, lifecycle_reason) "
                        + "VALUES (?, ?, 'MEMBER', '2026-10-02 00:00:00', '2026-10-03 00:00:00', 'INACTIVE', "
                        + "'2026-10-02 12:00:00.000000', 'Approved end')",
                apartment.getId(), resident.getId());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM apartment_memberships WHERE apartment_id = ? AND status = 'INACTIVE' "
                        + "AND valid_to = '2026-10-03 00:00:00' "
                        + "AND lifecycle_changed_at = '2026-10-02 12:00:00.000000'",
                Integer.class, apartment.getId()));
    }

    @Test
    @Transactional
    void membershipCheckRejectsInvalidEffectiveEndAndTerminalStatusWithoutMetadata() {
        Apartment apartment = apartment("Invalid Membership Fixture Building", "M-02");
        Resident resident = resident("Invalid Membership Fixture Resident", "MEMBER-02");
        entityManager.persist(apartment);
        entityManager.persist(resident);
        entityManager.flush();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO apartment_memberships "
                        + "(apartment_id, resident_id, member_role, valid_from, valid_to, status) "
                        + "VALUES (?, ?, 'MEMBER', '2026-10-02 00:00:00', '2026-10-02 00:00:00', 'ACTIVE')",
                apartment.getId(), resident.getId()));
    }

    @Test
    @Transactional
    void membershipTerminalLifecycleStatesRequireTimeAndReasonMetadata() {
        Apartment apartment = apartment("Membership Lifecycle Fixture Building", "M-03");
        Resident resident = resident("Membership Lifecycle Fixture Resident", "MEMBER-03");
        entityManager.persist(apartment);
        entityManager.persist(resident);
        entityManager.flush();

        for (String status : List.of("INACTIVE", "REVOKED", "VOID")) {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO apartment_memberships "
                            + "(apartment_id, resident_id, member_role, valid_from, status) "
                            + "VALUES (?, ?, 'MEMBER', '2026-10-02 00:00:00', ?)",
                    apartment.getId(), resident.getId(), status), "status=" + status);
        }

        for (String status : List.of("REVOKED", "VOID")) {
            jdbcTemplate.update(
                    "INSERT INTO apartment_memberships "
                            + "(apartment_id, resident_id, member_role, valid_from, status, "
                            + "lifecycle_changed_at, lifecycle_reason) "
                            + "VALUES (?, ?, 'MEMBER', '2026-10-02 00:00:00', ?, "
                            + "'2026-10-02 01:00:00.000000', 'Approved lifecycle action')",
                    apartment.getId(), resident.getId(), status);
        }
    }

    @Test
    @Transactional
    void ownerAndAuthorizedUserRelationsMapDistinctGuarantorRequirements() {
        Vehicle vehicle = vehicle();
        Apartment guarantorApartment = apartment("Guarantor Context Building", "G-01");
        Resident owner = resident("Vehicle Owner Fixture", "OWNER-01");
        Resident authorized = resident("Authorized User Fixture", "AUTHORIZED-01");
        Resident householdHeadAuthorized = resident("Household-Head Authorized Fixture", "AUTHORIZED-02");
        entityManager.persist(guarantorApartment);
        entityManager.persist(owner);
        entityManager.persist(authorized);
        entityManager.persist(householdHeadAuthorized);
        entityManager.flush();

        VehicleResidentRelation ownerRelation = new VehicleResidentRelation();
        ownerRelation.setVehicle(vehicle);
        ownerRelation.setResident(owner);
        ownerRelation.setRelationType(VehicleRelationType.OWNER);
        ownerRelation.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        ownerRelation.setStatus(VehicleRelationStatus.ACTIVE);
        entityManager.persist(ownerRelation);

        VehicleResidentRelation authorizedRelation = new VehicleResidentRelation();
        authorizedRelation.setVehicle(vehicle);
        authorizedRelation.setResident(authorized);
        authorizedRelation.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        authorizedRelation.setGuarantorType(VehicleRelationGuarantorType.OWNER);
        authorizedRelation.setGuarantorResident(owner);
        authorizedRelation.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        authorizedRelation.setStatus(VehicleRelationStatus.ACTIVE);
        entityManager.persist(authorizedRelation);

        VehicleResidentRelation householdHeadRelation = new VehicleResidentRelation();
        householdHeadRelation.setVehicle(vehicle);
        householdHeadRelation.setResident(householdHeadAuthorized);
        householdHeadRelation.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        householdHeadRelation.setGuarantorType(VehicleRelationGuarantorType.HOUSEHOLD_HEAD);
        householdHeadRelation.setGuarantorResident(owner);
        householdHeadRelation.setGuarantorApartment(guarantorApartment);
        householdHeadRelation.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        householdHeadRelation.setStatus(VehicleRelationStatus.ACTIVE);
        entityManager.persist(householdHeadRelation);
        entityManager.flush();

        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations WHERE vehicle_id = ?", Integer.class, vehicle.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations "
                        + "WHERE id = ? AND guarantor_apartment_id IS NOT NULL",
                Integer.class, authorizedRelation.getId()));
        assertEquals(guarantorApartment.getId(), jdbcTemplate.queryForObject(
                "SELECT guarantor_apartment_id FROM vehicle_resident_relations WHERE id = ?",
                Long.class, householdHeadRelation.getId()));
    }

    @Test
    @Transactional
    void vehicleRelationChecksRejectOwnerGuarantorFieldsAndAuthorizedUserWithoutGuarantor() {
        Vehicle vehicle = vehicle();
        Resident resident = resident("Relation Constraint Fixture", "RELATION-01");
        entityManager.persist(resident);
        entityManager.flush();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, guarantor_type, guarantor_resident_id, "
                        + "valid_from, status) VALUES (?, ?, 'OWNER', 'OWNER', ?, '2026-10-02 00:00:00', 'ACTIVE')",
                vehicle.getId(), resident.getId(), resident.getId()));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, valid_from, status) "
                        + "VALUES (?, ?, 'AUTHORIZED_USER', '2026-10-02 00:00:00', 'ACTIVE')",
                vehicle.getId(), resident.getId()));
    }

    @Test
    void existingVehicleRelationsMeetAhrReviewGuarantorPreflight() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations WHERE "
                        + "(relation_type = 'OWNER' AND (guarantor_type IS NOT NULL "
                        + "OR guarantor_resident_id IS NOT NULL OR guarantor_apartment_id IS NOT NULL "
                        + "OR status = 'PRE_EFFECTIVE_CANCELLED')) OR "
                        + "(relation_type = 'AUTHORIZED_USER' AND (guarantor_type IS NULL "
                        + "OR guarantor_resident_id IS NULL "
                        + "OR (guarantor_type = 'OWNER' AND guarantor_apartment_id IS NOT NULL) "
                        + "OR (guarantor_type = 'HOUSEHOLD_HEAD' AND guarantor_apartment_id IS NULL)))",
                Integer.class));
    }

    @Test
    @Transactional
    void vehicleRelationGuarantorCheckRejectsAuthorizedUserWithNullType() {
        Vehicle vehicle = vehicle();
        Resident authorized = resident("Missing Guarantor Type User", "MGTU-01");
        Resident guarantor = resident("Missing Guarantor Type Resident", "MGTG-01");
        entityManager.persist(authorized);
        entityManager.persist(guarantor);
        entityManager.flush();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, guarantor_type, guarantor_resident_id, "
                        + "valid_from, status) VALUES (?, ?, 'AUTHORIZED_USER', NULL, ?, "
                        + "'2026-10-02 00:00:00', 'ACTIVE')",
                vehicle.getId(), authorized.getId(), guarantor.getId()));
    }

    @Test
    void v5MigrationAddsPendingVehicleRightTransitionStorage() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class));
        assertColumn("vehicle_right_pending_transitions", "vehicle_right_id", "bigint", "NO");
        assertColumn("vehicle_right_pending_transitions", "effective_at", "datetime", "NO");
        assertColumn("vehicle_right_pending_transitions", "reason", "varchar(500)", "NO");
        assertColumn("vehicle_right_pending_transitions", "source_actor_user_id", "bigint", "NO");
        assertEquals(1, uniqueIndexColumnCount(
                "vehicle_right_pending_transitions", "uk_vehicle_right_pending_transition_relation"));
        assertTrue(indexExists("vehicle_right_pending_transitions", "ix_vehicle_right_pending_transitions_due"));
        assertEquals("RESTRICT", foreignKeyDeleteRule("fk_vr_pending_transition_relation"));
        assertEquals("RESTRICT", foreignKeyDeleteRule("fk_vr_pending_transition_actor"));
        assertTrue(checkConstraintExists("ck_vehicle_right_pending_transition_reason"));
        assertEquals(1, columnCount("vehicle_resident_relations", "guarantor_type"));
        assertTrue(checkConstraintExists("ck_vehicle_relation_guarantor"));
    }

    @Test
    @Transactional
    void pendingVehicleRightTransitionPersistsItsRelationDueTimeReasonAndOriginActor() {
        Vehicle vehicle = vehicle();
        Resident owner = resident("Pending Transition Owner", "PENDING-OWNER-01");
        entityManager.persist(owner);
        entityManager.flush();

        VehicleResidentRelation vehicleRight = new VehicleResidentRelation();
        vehicleRight.setVehicle(vehicle);
        vehicleRight.setResident(owner);
        vehicleRight.setRelationType(VehicleRelationType.OWNER);
        vehicleRight.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        vehicleRight.setStatus(VehicleRelationStatus.ACTIVE);
        entityManager.persist(vehicleRight);

        User actor = new User();
        actor.setUsername("ahrr01.pending." + java.util.UUID.randomUUID());
        actor.setStatus(UserStatus.ACTIVE);
        actor.setCreatedAt(LocalDateTime.now());
        entityManager.persist(actor);
        entityManager.flush();

        LocalDateTime effectiveAt = LocalDateTime.of(2026, 11, 2, 0, 0);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_right_pending_transitions "
                        + "(vehicle_right_id, effective_at, reason, source_actor_user_id) VALUES (?, ?, ?, ?)",
                vehicleRight.getId(), effectiveAt, "   ", actor.getId()));

        VehicleRightPendingTransition transition = new VehicleRightPendingTransition();
        transition.setVehicleRight(vehicleRight);
        transition.setEffectiveAt(effectiveAt);
        transition.setReason("Owner transfer takes effect later");
        transition.setSourceActor(actor);
        entityManager.persist(transition);
        entityManager.flush();

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_right_pending_transitions WHERE id = ? AND vehicle_right_id = ? "
                        + "AND effective_at = ? AND reason = ? AND source_actor_user_id = ?",
                Integer.class, transition.getId(), vehicleRight.getId(), effectiveAt,
                transition.getReason(), actor.getId()));
        assertEquals(List.of(transition.getId()), pendingTransitionRepository.findDueIds(
                effectiveAt, org.springframework.data.domain.PageRequest.of(0, 10)));
        assertEquals(transition.getId(), pendingTransitionRepository.findByIdForUpdate(transition.getId())
                .orElseThrow().getId());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_right_pending_transitions "
                        + "(vehicle_right_id, effective_at, reason, source_actor_user_id) VALUES (?, ?, ?, ?)",
                vehicleRight.getId(), effectiveAt, "Duplicate pending transition", actor.getId()));
    }

    @Test
    @Transactional
    void preEffectiveCancelledAuthorizedUserUsesLifecycleTimeWithoutAnEffectiveEnd() {
        Vehicle vehicle = vehicle();
        Resident owner = resident("Scheduled Grant Guarantor", "SCHEDULED-OWNER");
        Resident authorized = resident("Scheduled Grant User", "SCHEDULED-USER");
        entityManager.persist(owner);
        entityManager.persist(authorized);
        entityManager.flush();

        VehicleResidentRelation cancelled = new VehicleResidentRelation();
        cancelled.setVehicle(vehicle);
        cancelled.setResident(authorized);
        cancelled.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        cancelled.setGuarantorType(VehicleRelationGuarantorType.OWNER);
        cancelled.setGuarantorResident(owner);
        cancelled.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        cancelled.setStatus(VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED);
        cancelled.setLifecycleChangedAt(LocalDateTime.of(2026, 10, 2, 0, 0));
        cancelled.setLifecycleReason("Guarantor authority ended before start");
        entityManager.persist(cancelled);
        entityManager.flush();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations "
                        + "WHERE id = ? AND valid_to IS NOT NULL",
                Integer.class, cancelled.getId()));
        assertEquals(LocalDateTime.of(2026, 10, 2, 0, 0), jdbcTemplate.queryForObject(
                "SELECT lifecycle_changed_at FROM vehicle_resident_relations WHERE id = ?",
                LocalDateTime.class, cancelled.getId()));
    }

    @Test
    @Transactional
    void preEffectiveCancelledCheckRejectsCancellationAfterScheduledStart() {
        Vehicle vehicle = vehicle();
        Resident owner = resident("Late Cancellation Guarantor", "LATE-OWNER");
        Resident authorized = resident("Late Cancellation User", "LATE-USER");
        entityManager.persist(owner);
        entityManager.persist(authorized);
        entityManager.flush();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, guarantor_type, guarantor_resident_id, "
                        + "valid_from, status, lifecycle_changed_at, lifecycle_reason) "
                        + "VALUES (?, ?, 'AUTHORIZED_USER', 'OWNER', ?, '2026-10-03 00:00:00', "
                        + "'PRE_EFFECTIVE_CANCELLED', '2026-10-04 00:00:00', 'Late cancellation')",
                vehicle.getId(), authorized.getId(), owner.getId()));
    }

    @Test
    @Transactional
    void vehicleTerminalLifecycleStatesRequireTheirDistinctEffectiveAndLifecycleFields() {
        Vehicle vehicle = vehicle();
        Resident resident = resident("Vehicle Lifecycle Fixture", "VEHICLE-LIFECYCLE-01");
        Resident guarantor = resident("Vehicle Lifecycle Guarantor", "VEHICLE-LIFECYCLE-02");
        entityManager.persist(resident);
        entityManager.persist(guarantor);
        entityManager.flush();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, guarantor_type, guarantor_resident_id, "
                        + "valid_from, status, lifecycle_changed_at, lifecycle_reason) "
                        + "VALUES (?, ?, 'AUTHORIZED_USER', 'OWNER', ?, '2026-10-02 00:00:00', 'INACTIVE', "
                        + "'2026-10-02 01:00:00.000000', 'Missing effective end')",
                vehicle.getId(), resident.getId(), guarantor.getId()));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO vehicle_resident_relations "
                        + "(vehicle_id, resident_id, relation_type, guarantor_type, guarantor_resident_id, "
                        + "valid_from, status, lifecycle_changed_at, lifecycle_reason) "
                        + "VALUES (?, ?, 'AUTHORIZED_USER', 'OWNER', ?, '2026-10-02 00:00:00', 'REVOKED', "
                        + "'2026-10-02 01:00:00.000000', '   ')",
                vehicle.getId(), resident.getId(), guarantor.getId()));

        VehicleResidentRelation ended = new VehicleResidentRelation();
        ended.setVehicle(vehicle);
        ended.setResident(resident);
        ended.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        ended.setGuarantorType(VehicleRelationGuarantorType.OWNER);
        ended.setGuarantorResident(guarantor);
        ended.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        ended.setValidTo(LocalDateTime.of(2026, 10, 3, 0, 0));
        ended.setStatus(VehicleRelationStatus.INACTIVE);
        ended.setLifecycleChangedAt(LocalDateTime.of(2026, 10, 4, 0, 0));
        ended.setLifecycleReason("Recorded after effective end");
        entityManager.persist(ended);

        VehicleResidentRelation revoked = new VehicleResidentRelation();
        revoked.setVehicle(vehicle);
        revoked.setResident(resident);
        revoked.setRelationType(VehicleRelationType.AUTHORIZED_USER);
        revoked.setGuarantorType(VehicleRelationGuarantorType.OWNER);
        revoked.setGuarantorResident(guarantor);
        revoked.setValidFrom(LocalDateTime.of(2026, 10, 4, 0, 0));
        revoked.setStatus(VehicleRelationStatus.REVOKED);
        revoked.setLifecycleChangedAt(LocalDateTime.of(2026, 10, 4, 1, 0));
        revoked.setLifecycleReason("Explicit withdrawal");
        entityManager.persist(revoked);

        Vehicle voidVehicle = vehicle();
        Resident voidOwner = resident("Created In Error Owner", "VOID-OWNER-01");
        entityManager.persist(voidOwner);
        entityManager.flush();
        VehicleResidentRelation voidOwnerRelation = new VehicleResidentRelation();
        voidOwnerRelation.setVehicle(voidVehicle);
        voidOwnerRelation.setResident(voidOwner);
        voidOwnerRelation.setRelationType(VehicleRelationType.OWNER);
        voidOwnerRelation.setValidFrom(LocalDateTime.of(2026, 10, 2, 0, 0));
        voidOwnerRelation.setStatus(VehicleRelationStatus.VOID);
        voidOwnerRelation.setLifecycleChangedAt(LocalDateTime.of(2026, 10, 2, 1, 0));
        voidOwnerRelation.setLifecycleReason("Created in error");
        entityManager.persist(voidOwnerRelation);
        entityManager.flush();

        assertEquals(List.of("INACTIVE", "REVOKED"), jdbcTemplate.queryForList(
                "SELECT status FROM vehicle_resident_relations WHERE vehicle_id = ? "
                        + "AND status IN ('INACTIVE','REVOKED') ORDER BY status",
                String.class, vehicle.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_resident_relations WHERE id = ? AND status = 'VOID' "
                        + "AND lifecycle_changed_at IS NOT NULL AND lifecycle_reason = 'Created in error'",
                Integer.class, voidOwnerRelation.getId()));
    }

    private void assertColumn(String table, String column, String expectedType, String expectedNullable) {
        assertEquals(expectedType, columnType(table, column).toLowerCase());
        assertEquals(expectedNullable, jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column));
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT column_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
    }

    private int uniqueIndexColumnCount(String table, String index) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? AND non_unique = 0",
                Integer.class, table, index);
    }

    private boolean indexExists(String table, String index) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index) > 0;
    }

    private boolean checkConstraintExists(String constraint) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = DATABASE() AND constraint_name = ? AND constraint_type = 'CHECK'",
                Integer.class, constraint) == 1;
    }

    private String foreignKeyDeleteRule(String constraint) {
        return jdbcTemplate.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND constraint_name = ?",
                String.class, constraint);
    }

    private Apartment apartment(String building, String apartmentCode) {
        Apartment apartment = new Apartment();
        apartment.setBuilding(building);
        apartment.setApartmentCode(apartmentCode);
        apartment.setStatus(ApartmentStatus.ACTIVE);
        return apartment;
    }

    private Resident resident(String fullName, String identityNumber) {
        Resident resident = new Resident();
        resident.setFullName(fullName);
        resident.setIdentityNumber(identityNumber);
        resident.setStatus(ResidentStatus.ACTIVE);
        return resident;
    }

    private Vehicle vehicle() {
        VehicleFamily family = new VehicleFamily();
        family.setCode("AHR01-FAMILY");
        family.setName("AHR-01 test fixture");
        entityManager.persist(family);

        VehicleCategory category = new VehicleCategory();
        category.setFamily(family);
        category.setCode("AHR01-CATEGORY");
        category.setName("AHR-01 test fixture");
        entityManager.persist(category);

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleCategory(category);
        vehicle.setPlateNumber("AHR01-TEST-PLATE");
        vehicle.setPlateNormalized("AHR01-TEST-PLATE");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        entityManager.persist(vehicle);
        entityManager.flush();
        return vehicle;
    }
}
