package vn.edu.huit.smartparking.backend.resident.service;

import org.springframework.stereotype.Service;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentReactivationRequest;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class ApartmentStatusManagementService {
    private static final int MAX_DISCOVERY_RETRIES = 2;

    private final ApartmentStatusTransactionService transactionService;

    public ApartmentStatusManagementService(ApartmentStatusTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public ApartmentDetail deactivate(Long id, ApartmentDeactivationRequest request, User actor) {
        for (int attempt = 0; attempt <= MAX_DISCOVERY_RETRIES; attempt++) {
            try {
                return transactionService.deactivate(id, request, actor);
            } catch (ApartmentStatusTransactionService.MembershipDiscoveryChangedException exception) {
                if (attempt == MAX_DISCOVERY_RETRIES) {
                    throw new ApartmentConcurrentModificationException();
                }
            }
        }
        throw new ApartmentConcurrentModificationException();
    }

    public ApartmentDetail reactivate(Long id, ApartmentReactivationRequest request, User actor) {
        return transactionService.reactivate(id, request, actor);
    }
}
