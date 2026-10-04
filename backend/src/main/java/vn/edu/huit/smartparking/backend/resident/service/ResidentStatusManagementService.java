package vn.edu.huit.smartparking.backend.resident.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.StatusMembershipAction;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.VehicleRightAction;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class ResidentStatusManagementService {
    private static final int MAX_LOCK_SET_RETRIES = 2;

    private final ResidentStatusTransactionService transactionService;
    private final VehicleRightInvalidationPort vehicleRightInvalidationPort;
    private final TransactionTemplate transactionTemplate;

    public ResidentStatusManagementService(
            ResidentStatusTransactionService transactionService,
            VehicleRightInvalidationPort vehicleRightInvalidationPort,
            PlatformTransactionManager transactionManager) {
        this.transactionService = transactionService;
        this.vehicleRightInvalidationPort = vehicleRightInvalidationPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ResidentDetail changeStatus(Long id, ResidentStatusChangeRequest request, User actor) {
        validateRequest(id, request);
        LocalDateTime commandTime = LocalDateTime.now().withNano(0);
        List<VehicleRightAction> vehicleActions = vehicleActions(request);
        List<StatusMembershipAction> membershipActions = membershipActions(request);
        ResidentStatusResources resources = new ResidentStatusResources(List.of(id), List.of(), List.of());
        if (request.status() != vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus.ACTIVE
                || !vehicleActions.isEmpty()
                || !membershipActions.isEmpty()) {
            resources = resources.merge(vehicleRightInvalidationPort.discoverResidentStatusResources(
                    id, request.status(), commandTime, vehicleActions, membershipActions));
        }

        for (int attempt = 0; attempt <= MAX_LOCK_SET_RETRIES; attempt++) {
            ResidentStatusResources lockSet = resources;
            try {
                return transactionTemplate.execute(status ->
                        transactionService.changeStatus(
                                id, request, actor, commandTime, lockSet, vehicleActions, membershipActions));
            } catch (ResidentStatusLockSetChangedException exception) {
                resources = resources.merge(exception.discoveredResources());
                if (attempt == MAX_LOCK_SET_RETRIES) {
                    throw new ResidentConcurrentModificationException();
                }
            }
        }
        throw new ResidentConcurrentModificationException();
    }

    private List<VehicleRightAction> vehicleActions(ResidentStatusChangeRequest request) {
        if (request.vehicleRightActions() == null) {
            return List.of();
        }
        return request.vehicleRightActions().stream()
                .map(action -> new VehicleRightAction(action.relationId(), action.action(),
                        action.effectiveAt(), action.reason()))
                .toList();
    }

    private List<StatusMembershipAction> membershipActions(ResidentStatusChangeRequest request) {
        if (request.membershipActions() == null) {
            return List.of();
        }
        return request.membershipActions().stream()
                .map(action -> new StatusMembershipAction(
                        action.membershipId(), action.action(), action.effectiveAt(), action.reason()))
                .toList();
    }

    private void validateRequest(Long id, ResidentStatusChangeRequest request) {
        if (id == null || id <= 0 || request == null || request.status() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new ResidentInvalidRequestException();
        }
        TreeSet<Long> membershipIds = new TreeSet<>();
        if (request.membershipActions() != null) {
            for (ResidentStatusChangeRequest.MembershipAction action : request.membershipActions()) {
                if (action == null || !positive(action.membershipId()) || action.action() == null
                        || action.effectiveAt() == null || action.reason() == null || action.reason().isBlank()
                        || action.reason().length() > 500 || !membershipIds.add(action.membershipId())) {
                    throw new ResidentInvalidRequestException();
                }
            }
        }
        TreeSet<Long> vehicleRelationIds = new TreeSet<>();
        if (request.vehicleRightActions() != null) {
            for (ResidentStatusChangeRequest.VehicleRightAction action : request.vehicleRightActions()) {
                if (action == null || !positive(action.relationId()) || action.action() == null
                        || action.effectiveAt() == null || action.reason() == null || action.reason().isBlank()
                        || action.reason().length() > 500 || !vehicleRelationIds.add(action.relationId())) {
                    throw new ResidentInvalidRequestException();
                }
            }
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }
}
