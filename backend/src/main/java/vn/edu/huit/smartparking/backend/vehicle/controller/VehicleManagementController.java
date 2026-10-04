package vn.edu.huit.smartparking.backend.vehicle.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerTransferRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.AuthorizedUserGrantRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightLifecycleRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightVoidRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleSummary;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.service.AuthorizedUserGrantService;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleManagementService;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightLifecycleService;

@RestController
@RequestMapping("/api/management")
public class VehicleManagementController {
    private final VehicleManagementService vehicleService;
    private final AuthorizedUserGrantService authorizedUserService;
    private final VehicleRightLifecycleService vehicleRightLifecycleService;
    private final UserRepository userRepository;

    public VehicleManagementController(
            VehicleManagementService vehicleService,
            AuthorizedUserGrantService authorizedUserService,
            VehicleRightLifecycleService vehicleRightLifecycleService,
            UserRepository userRepository) {
        this.vehicleService = vehicleService;
        this.authorizedUserService = authorizedUserService;
        this.vehicleRightLifecycleService = vehicleRightLifecycleService;
        this.userRepository = userRepository;
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_READ')")
    public PagedResponse<VehicleSummary> listVehicles(
            @RequestParam(name = "plate_number", required = false) String plateNumber,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return vehicleService.list(plateNumber, page, size);
    }

    @GetMapping("/vehicles/{id}")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_READ')")
    public VehicleSummary vehicleDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return vehicleService.detail(id, actor(account));
    }

    @PostMapping("/vehicles/{id}/owner")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public ResponseEntity<VehicleRightDetail> assignOwner(
            @PathVariable("id") Long id,
            @Valid @RequestBody VehicleOwnerAssignmentRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        VehicleRightDetail created = vehicleService.assignOwner(id, request, actor(account));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/vehicles/{id}/owner/transfer")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public VehicleRightDetail transferOwner(
            @PathVariable("id") Long id,
            @Valid @RequestBody VehicleOwnerTransferRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return vehicleService.transferOwner(id, request, actor(account));
    }

    @GetMapping("/vehicle-rights")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_READ')")
    public PagedResponse<VehicleRightDetail> listVehicleRights(
            @RequestParam(name = "vehicle_id", required = false) Long vehicleId,
            @RequestParam(name = "resident_id", required = false) Long residentId,
            @RequestParam(name = "relation_type", required = false) VehicleRelationType relationType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return authorizedUserService.list(vehicleId, residentId, relationType, page, size);
    }

    @GetMapping("/vehicle-rights/{id}")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_READ')")
    public VehicleRightDetail vehicleRightDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return authorizedUserService.detail(id, actor(account));
    }

    @GetMapping("/vehicle-rights/{id}/history")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_READ')")
    public PagedResponse<HistoryItem> vehicleRightHistory(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return authorizedUserService.history(id, page, size);
    }

    @PostMapping("/vehicle-rights")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public ResponseEntity<VehicleRightDetail> grantAuthorizedUser(
        @Valid @RequestBody AuthorizedUserGrantRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        VehicleRightDetail created = authorizedUserService.grantAuthorizedUser(
                request == null ? null : request.vehicleId(), request, actor(account));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/vehicle-rights/{id}/end")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public VehicleRightDetail endVehicleRight(
            @PathVariable Long id,
            @Valid @RequestBody VehicleRightLifecycleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return vehicleRightLifecycleService.end(id, request, actor(account));
    }

    @PostMapping("/vehicle-rights/{id}/revoke")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public VehicleRightDetail revokeVehicleRight(
            @PathVariable Long id,
            @Valid @RequestBody VehicleRightLifecycleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return vehicleRightLifecycleService.revoke(id, request, actor(account));
    }

    @PostMapping("/vehicle-rights/{id}/void")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public VehicleRightDetail voidVehicleRight(
            @PathVariable Long id,
            @Valid @RequestBody VehicleRightVoidRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return vehicleRightLifecycleService.voidRight(id, request, actor(account));
    }

    private User actor(AuthenticatedAccount account) {
        return userRepository.findById(account.userId()).orElse(null);
    }
}
