package vn.edu.huit.smartparking.backend.resident.controller;

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
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipLifecycleRequest;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipTransferRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipVoidRequest;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;

@RestController
@RequestMapping("/api/management")
public class ApartmentMembershipManagementController {
    private final ApartmentMembershipManagementService membershipService;
    private final UserRepository userRepository;

    public ApartmentMembershipManagementController(
            ApartmentMembershipManagementService membershipService,
            UserRepository userRepository) {
        this.membershipService = membershipService;
        this.userRepository = userRepository;
    }

    @GetMapping("/memberships")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_READ')")
    public PagedResponse<MembershipDetail> list(
            @RequestParam(name = "apartment_id", required = false) Long apartmentId,
            @RequestParam(name = "resident_id", required = false) Long residentId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return membershipService.list(apartmentId, residentId, page, size);
    }

    @PostMapping("/memberships")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public ResponseEntity<MembershipDetail> add(
            @Valid @RequestBody MembershipCreateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        MembershipDetail created = membershipService.add(request, actor(account));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/apartments/{apartmentId}/household-head/assign")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public ResponseEntity<MembershipDetail> assignHouseholdHead(
            @PathVariable Long apartmentId,
            @Valid @RequestBody MembershipHeadAssignRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        MembershipDetail created = membershipService.assignHouseholdHead(apartmentId, request, actor(account));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/apartments/{apartmentId}/household-head/transfer")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public MembershipDetail transferHouseholdHead(
            @PathVariable("apartmentId") Long apartmentId,
            @Valid @RequestBody MembershipTransferRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return membershipService.transferHouseholdHead(apartmentId, request, actor(account));
    }

    @GetMapping("/memberships/{id}")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_READ')")
    public MembershipDetail detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return membershipService.detail(id, actor(account));
    }

    @GetMapping("/memberships/{id}/history")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_READ')")
    public PagedResponse<HistoryItem> history(
            @PathVariable Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return membershipService.history(id, page, size);
    }

    @PostMapping("/memberships/{id}/end")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public MembershipDetail end(
            @PathVariable Long id,
            @Valid @RequestBody MembershipLifecycleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return membershipService.end(id, request, actor(account));
    }

    @PostMapping("/memberships/{id}/revoke")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public MembershipDetail revoke(
            @PathVariable Long id,
            @Valid @RequestBody MembershipLifecycleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return membershipService.revoke(id, request, actor(account));
    }

    @PostMapping("/memberships/{id}/void")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public MembershipDetail voidMembership(
            @PathVariable Long id,
            @Valid @RequestBody MembershipVoidRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return membershipService.voidMembership(id, request, actor(account));
    }

    private User actor(AuthenticatedAccount account) {
        return userRepository.findById(account.userId()).orElse(null);
    }
}
