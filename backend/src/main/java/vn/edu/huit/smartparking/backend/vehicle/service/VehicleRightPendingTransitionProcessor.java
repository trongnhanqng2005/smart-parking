package vn.edu.huit.smartparking.backend.vehicle.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ResidentRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleRightPendingTransition;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleResidentRelationRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRightPendingTransitionRepository;

@Service
public class VehicleRightPendingTransitionProcessor {
    private static final int BATCH_SIZE = 100;

    private final VehicleRightPendingTransitionRepository pendingRepository;
    private final VehicleResidentRelationRepository relationRepository;
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleRightInvalidationService invalidationService;
    private final TransactionTemplate transactionTemplate;

    public VehicleRightPendingTransitionProcessor(
            VehicleRightPendingTransitionRepository pendingRepository,
            VehicleResidentRelationRepository relationRepository,
            ResidentRepository residentRepository,
            ApartmentRepository apartmentRepository,
            VehicleRepository vehicleRepository,
            VehicleRightInvalidationService invalidationService,
            PlatformTransactionManager transactionManager) {
        this.pendingRepository = pendingRepository;
        this.relationRepository = relationRepository;
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.invalidationService = invalidationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${smart-parking.vehicle-right-transitions.fixed-delay-ms:60000}")
    public void processDueTransitions() {
        LocalDateTime dueAt = LocalDateTime.now();
        List<Long> dueIds = pendingRepository.findDueIds(dueAt, PageRequest.of(0, BATCH_SIZE));
        dueIds.forEach(id -> processPendingTransition(id, dueAt));
    }

    public boolean processPendingTransition(Long pendingId, LocalDateTime now) {
        if (pendingId == null || now == null) {
            throw new IllegalArgumentException("Pending transition id and processing time are required");
        }
        return transactionTemplate.execute(status -> processPendingTransitionInTransaction(pendingId, now));
    }

    private boolean processPendingTransitionInTransaction(Long pendingId, LocalDateTime now) {
        VehicleRightPendingTransition discovered = pendingRepository.findById(pendingId).orElse(null);
        if (discovered == null || discovered.getEffectiveAt().isAfter(now)) {
            return false;
        }

        VehicleResidentRelation discoveredRelation = discovered.getVehicleRight();
        TreeSet<Long> residentIds = new TreeSet<>();
        residentIds.add(discoveredRelation.getResident().getId());
        if (discoveredRelation.getGuarantorResident() != null) {
            residentIds.add(discoveredRelation.getGuarantorResident().getId());
        }
        residentIds.forEach(id -> residentRepository.findByIdForUpdate(id)
                .orElseThrow(VehicleNotFoundException::new));

        if (discoveredRelation.getGuarantorApartment() != null) {
            apartmentRepository.findByIdForUpdate(discoveredRelation.getGuarantorApartment().getId())
                    .orElseThrow(VehicleNotFoundException::new);
        }
        vehicleRepository.findByIdForUpdate(discoveredRelation.getVehicle().getId())
                .orElseThrow(VehicleNotFoundException::new);
        VehicleResidentRelation relation = relationRepository.findByIdForUpdate(discoveredRelation.getId())
                .orElse(null);
        VehicleRightPendingTransition pending = pendingRepository.findByIdForUpdate(pendingId).orElse(null);
        if (pending == null || relation == null
                || !pending.getVehicleRight().getId().equals(relation.getId())) {
            return false;
        }

        if (relation.getStatus() != VehicleRelationStatus.ACTIVE
                || relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                || !pending.getEffectiveAt().equals(relation.getValidTo())) {
            pendingRepository.delete(pending);
            return false;
        }

        User actor = pending.getSourceActor();
        invalidationService.processDueTransition(relation, pending.getEffectiveAt(), pending.getReason(), actor);
        pendingRepository.delete(pending);
        return true;
    }
}
