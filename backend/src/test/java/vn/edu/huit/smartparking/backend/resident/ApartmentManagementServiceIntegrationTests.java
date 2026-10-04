package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;

@SpringBootTest
@ActiveProfiles("test")
class ApartmentManagementServiceIntegrationTests {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ApartmentManagementService apartmentService;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsActiveApartmentAndLooksItUpByCanonicalIdentity() {
        String building = "AHR03 Create " + UUID.randomUUID();

        ApartmentDetail created = apartmentService.create(
                new ApartmentCreateRequest(building, "A-01", 7), null);

        assertNotNull(created.id());
        assertEquals(building, created.building());
        assertEquals("A-01", created.apartmentCode());
        assertEquals(7, created.floorNo());
        assertEquals(ApartmentStatus.ACTIVE, created.status());
        assertNotNull(created.createdAt());
        assertNotNull(created.updatedAt());
        assertEquals(created.id(), apartmentRepository
                .findByCanonicalBusinessIdentity(building.toUpperCase(), "a-01").orElseThrow().getId());
    }

    @Test
    @Transactional
    void rejectsEquivalentNormalizedApartmentIdentity() {
        String building = "AHR03 Duplicate " + UUID.randomUUID();
        apartmentService.create(new ApartmentCreateRequest(building, "A-01", null), null);
        ApartmentDetail second = apartmentService.create(new ApartmentCreateRequest(building, "A-02", null), null);

        assertThrows(ApartmentIdentityConflictException.class,
                () -> apartmentService.create(new ApartmentCreateRequest(
                        "  " + building.toUpperCase() + "  ", "a-01", null), null));

        ApartmentCorrectionRequest correction = new ApartmentCorrectionRequest();
        correction.setApartmentCode("A-01");
        correction.setReason("Corrected against the apartment register");
        assertThrows(ApartmentIdentityConflictException.class,
                () -> apartmentService.correct(second.id(), correction, null));
    }

    @Test
    @Transactional
    void rejectsIdentityFieldsThatNormalizeToBlankAsInvalidRequests() {
        assertThrows(ApartmentInvalidRequestException.class,
                () -> apartmentService.create(new ApartmentCreateRequest("\u00a0", "A-01", null), null));
    }

    @Test
    @Transactional
    void correctionRequiresReasonForIdentityChangeAndExplicitNullClearsFloor() {
        String building = "AHR03 Correction " + UUID.randomUUID();
        ApartmentDetail created = apartmentService.create(
                new ApartmentCreateRequest(building, "A-01", 7), null);
        Apartment apartment = apartmentRepository.findById(created.id()).orElseThrow();
        Resident resident = new Resident();
        resident.setFullName("AHR03 Synthetic Resident");
        resident.setIdentityNumber("AHR-" + UUID.randomUUID().toString().substring(0, 16));
        resident.setStatus(ResidentStatus.ACTIVE);
        entityManager.persist(resident);
        ApartmentMembership membership = new ApartmentMembership();
        membership.setApartment(apartment);
        membership.setResident(resident);
        membership.setMemberRole(MembershipRole.MEMBER);
        membership.setValidFrom(LocalDateTime.now().minusDays(1));
        membership.setStatus(MembershipStatus.ACTIVE);
        entityManager.persist(membership);
        entityManager.flush();
        Long membershipId = membership.getId();
        ApartmentCorrectionRequest correction = new ApartmentCorrectionRequest();
        correction.setBuilding(building + " Revised");
        correction.setFloorNo(null);

        assertThrows(ApartmentInvalidRequestException.class,
                () -> apartmentService.correct(created.id(), correction, null));

        correction.setReason("Verified apartment label correction");
        ApartmentDetail corrected = apartmentService.correct(created.id(), correction, null);

        assertEquals(building + " Revised", corrected.building());
        assertEquals(null, corrected.floorNo());
        assertEquals(created.apartmentCode(), corrected.apartmentCode());
        ApartmentMembership unchangedMembership = entityManager.find(ApartmentMembership.class, membershipId);
        assertEquals(created.id(), unchangedMembership.getApartment().getId());
        assertEquals(resident.getId(), unchangedMembership.getResident().getId());
        assertEquals(MembershipStatus.ACTIVE, unchangedMembership.getStatus());

        PagedResponse<HistoryItem> history = apartmentService.history(created.id(), 0, 20);
        assertEquals(List.of("APARTMENT_CORRECTED", "APARTMENT_CREATED"),
                history.items().stream().map(HistoryItem::action).toList());
        assertEquals("Verified apartment label correction", history.items().getFirst().reason());
    }

    @Test
    @Transactional
    void apartmentListUsesExactCanonicalFiltersAndReturnsSummaries() {
        String building = "AHR03 List " + UUID.randomUUID();
        apartmentService.create(new ApartmentCreateRequest(building, "A-01", 7), null);
        apartmentService.create(new ApartmentCreateRequest(building, "A-02", 8), null);

        PagedResponse<ApartmentSummary> page = apartmentService.list(
                "  " + building.toUpperCase() + "  ", "a-01", 0, 1);
        PagedResponse<ApartmentSummary> partial = apartmentService.list("AHR03 List", null, 0, 20);
        PagedResponse<ApartmentSummary> ordered = apartmentService.list(building, null, 0, 20);
        PagedResponse<ApartmentSummary> secondPage = apartmentService.list(building, null, 1, 1);

        assertEquals(1, page.totalItems());
        assertEquals(1, page.items().size());
        assertEquals("A-01", page.items().getFirst().apartmentCode());
        assertEquals(0, partial.totalItems());
        assertEquals(2, ordered.totalItems());
        assertEquals(1, secondPage.items().size());
        assertEquals("A-01", secondPage.items().getFirst().apartmentCode());
        assertThrows(ApartmentInvalidRequestException.class,
                () -> apartmentService.list(null, null, -1, 20));
    }

    @Test
    @Transactional
    void detailReadsAreAuditedButListAndHistoryReadsAreNot() {
        String building = "AHR03 Read Audit " + UUID.randomUUID();
        ApartmentDetail created = apartmentService.create(
                new ApartmentCreateRequest(building, "A-01", null), null);
        int beforeRead = auditCount(created.id());

        apartmentService.list(building, null, 0, 20);
        apartmentService.history(created.id(), 0, 20);
        assertEquals(beforeRead, auditCount(created.id()));

        apartmentService.detail(created.id(), null);

        assertEquals(beforeRead + 1, auditCount(created.id()));
        PagedResponse<HistoryItem> history = apartmentService.history(created.id(), 0, 20);
        assertEquals("APARTMENT_DETAIL_READ", history.items().getFirst().action());
        assertEquals(created.id(), history.items().getFirst().subjectId());
    }

    @Test
    void concurrentEquivalentCreatesReturnOneApartmentAndOneConflict() throws Exception {
        String building = "AHR03 Concurrent " + UUID.randomUUID();
        String equivalentBuilding = "  " + building.toUpperCase() + "  ";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ApartmentDetail>> results = List.of(
                executor.submit(() -> concurrentCreate(building, "A-01", ready, start)),
                executor.submit(() -> concurrentCreate(equivalentBuilding, "a-01", ready, start)));
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int created = 0;
            int conflicts = 0;
            for (Future<ApartmentDetail> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    created++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof ApartmentIdentityConflictException);
                    conflicts++;
                }
            }
            assertEquals(1, created);
            assertEquals(1, conflicts);
            assertEquals(1, apartmentRepository.findByCanonicalBusinessIdentity(building, "A-01").stream().count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            apartmentRepository.findByCanonicalBusinessIdentity(building, "A-01").ifPresent(apartment -> {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ?",
                        apartment.getId().toString());
                apartmentRepository.deleteById(apartment.getId());
            });
        }
    }

    private ApartmentDetail concurrentCreate(
            String building, String apartmentCode, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent Apartment test did not start");
        }
        return apartmentService.create(new ApartmentCreateRequest(building, apartmentCode, null), null);
    }

    private int auditCount(Long apartmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'APARTMENT' AND entity_id = ?",
                Integer.class, apartmentId.toString());
    }
}
