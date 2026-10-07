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
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentReactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusManagementService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;

@RestController
@RequestMapping("/api/management/apartments")
public class ApartmentManagementController {
    private final ApartmentManagementService apartmentService;
    private final ApartmentStatusManagementService apartmentStatusService;
    private final UserRepository userRepository;

    public ApartmentManagementController(
            ApartmentManagementService apartmentService,
            ApartmentStatusManagementService apartmentStatusService,
            UserRepository userRepository) {
        this.apartmentService = apartmentService;
        this.apartmentStatusService = apartmentStatusService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_READ')")
    public PagedResponse<ApartmentSummary> list(
            @RequestParam(name = "building", required = false) String building,
            @RequestParam(name = "apartment_code", required = false) String apartmentCode,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return apartmentService.list(building, apartmentCode, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_MANAGE')")
    public ResponseEntity<ApartmentDetail> create(
            @Valid @RequestBody ApartmentCreateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        ApartmentDetail created = apartmentService.create(request, actor(account));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_READ')")
    public ApartmentDetail detail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return apartmentService.detail(id, actor(account));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_READ')")
    public PagedResponse<HistoryItem> history(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return apartmentService.history(id, page, size);
    }

    @PatchMapping("/{id}/correction")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_MANAGE')")
    public ApartmentDetail correct(
            @PathVariable("id") Long id,
            @RequestBody ApartmentCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return apartmentService.correct(id, request, actor(account));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_MANAGE') "
            + "and (#request.membershipEnds == null or #request.membershipEnds.isEmpty() "
            + "or hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE'))")
    public ApartmentDetail deactivate(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApartmentDeactivationRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return apartmentStatusService.deactivate(id, request, actor(account));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_MANAGE')")
    public ApartmentDetail reactivate(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApartmentReactivationRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return apartmentStatusService.reactivate(id, request, actor(account));
    }

    private User actor(AuthenticatedAccount account) {
        return userRepository.findById(account.userId()).orElse(null);
    }
}
