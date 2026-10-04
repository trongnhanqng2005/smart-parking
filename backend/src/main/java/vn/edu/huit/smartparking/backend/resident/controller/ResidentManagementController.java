package vn.edu.huit.smartparking.backend.resident.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.service.ResidentCreateResult;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusManagementService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;

@RestController
@RequestMapping("/api/management/residents")
public class ResidentManagementController {
    private final ResidentManagementService residentService;
    private final ResidentStatusManagementService residentStatusService;
    private final UserRepository userRepository;

    public ResidentManagementController(
            ResidentManagementService residentService,
            ResidentStatusManagementService residentStatusService,
            UserRepository userRepository) {
        this.residentService = residentService;
        this.residentStatusService = residentStatusService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_READ')")
    public PagedResponse<ResidentSummary> list(
            @RequestParam(name = "full_name", required = false) String fullName,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return residentService.list(fullName, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_MANAGE')")
    public ResponseEntity<ResidentDetail> createOrReuse(
            @Valid @RequestBody ResidentCreateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        ResidentCreateResult result = residentService.createOrReuse(request, actor(account));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.resident());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_READ')")
    public ResidentDetail detail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return residentService.detail(id, actor(account));
    }

    @PostMapping("/lookup")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_READ')")
    public ResidentDetail lookupByIdentityNumber(
            @Valid @RequestBody ResidentLookupRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return residentService.lookupByIdentityNumber(request, actor(account));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_READ')")
    public PagedResponse<HistoryItem> history(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return residentService.history(id, page, size);
    }

    @PatchMapping("/{id}/correction")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_MANAGE')")
    public ResidentDetail correct(
            @PathVariable("id") Long id,
            @RequestBody ResidentCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return residentService.correct(id, request, actor(account));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_MANAGE') "
            + "and (#request.membershipActions == null or #request.membershipActions.isEmpty() "
            + "or hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')) "
            + "and (#request.vehicleRightActions == null or #request.vehicleRightActions.isEmpty() "
            + "or hasAuthority('VEHICLE_RIGHT_MANAGE'))")
    public ResidentDetail changeStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody ResidentStatusChangeRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return residentStatusService.changeStatus(id, request, actor(account));
    }

    private User actor(AuthenticatedAccount account) {
        return userRepository.findById(account.userId()).orElse(null);
    }
}
