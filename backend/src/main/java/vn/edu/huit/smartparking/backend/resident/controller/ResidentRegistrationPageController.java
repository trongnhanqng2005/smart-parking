package vn.edu.huit.smartparking.backend.resident.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.HouseholdHeadConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipOverlapException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentCreateResult;
import vn.edu.huit.smartparking.backend.resident.service.ResidentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentNotFoundException;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.vehicle.dto.AuthorizedUserGrantRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleSummary;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.service.AuthorizedUserGrantService;
import vn.edu.huit.smartparking.backend.vehicle.service.GuarantorChainConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleConcurrentModificationException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleInvalidRequestException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleManagementService;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleNotFoundException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleOwnerConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightOverlapException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleStatusConflictException;

@Controller
public class ResidentRegistrationPageController {
    private static final String VIEW = "app/resident-registration";

    private final ApartmentManagementService apartmentService;
    private final ResidentManagementService residentService;
    private final ApartmentMembershipManagementService membershipService;
    private final VehicleManagementService vehicleService;
    private final AuthorizedUserGrantService authorizedUserService;
    private final UserRepository userRepository;

    public ResidentRegistrationPageController(
            ApartmentManagementService apartmentService,
            ResidentManagementService residentService,
            ApartmentMembershipManagementService membershipService,
            VehicleManagementService vehicleService,
            AuthorizedUserGrantService authorizedUserService,
            UserRepository userRepository) {
        this.apartmentService = apartmentService;
        this.residentService = residentService;
        this.membershipService = membershipService;
        this.vehicleService = vehicleService;
        this.authorizedUserService = authorizedUserService;
        this.userRepository = userRepository;
    }

    @GetMapping("/management/resident-registration")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public String registration(
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            @RequestParam(name = "searchApartment", defaultValue = "false") boolean searchApartment,
            @RequestParam(name = "building", required = false) String building,
            @RequestParam(name = "apartmentCode", required = false) String apartmentCode,
            @RequestParam(name = "searchVehicle", defaultValue = "false") boolean searchVehicle,
            @RequestParam(name = "plateNumber", required = false) String plateNumber,
            @RequestParam(name = "vehicleId", required = false) Long vehicleId,
            Authentication authentication,
            Model model) {
        model.addAttribute("apartmentForm", new ApartmentForm());
        model.addAttribute("residentForm", new ResidentForm());
        model.addAttribute("vehicleSearchForm", new VehicleSearchForm(plateNumber));
        model.addAttribute("ownerForm", new OwnerForm());
        model.addAttribute("authorizedUserForm", new AuthorizedUserForm());
        model.addAttribute("apartmentId", apartmentId);
        model.addAttribute("residentId", residentId);
        model.addAttribute("vehicleId", vehicleId);
        setCapabilities(model, authentication);
        if (searchApartment && hasAuthority(authentication, "APARTMENT_READ")) {
            model.addAttribute("apartmentForm", new ApartmentForm(building, apartmentCode, null));
            try {
                PagedResponse<ApartmentSummary> results = apartmentService.list(building, apartmentCode, 0, 20);
                model.addAttribute("apartmentResults", results.items());
            } catch (ApartmentInvalidRequestException exception) {
                model.addAttribute("apartmentSearchError", "Vui lòng nhập tòa nhà và mã căn hộ hợp lệ.");
            }
        }
        if (searchVehicle && hasAuthority(authentication, "VEHICLE_RIGHT_READ")) {
            if (plateNumber == null || plateNumber.isBlank()) {
                model.addAttribute("vehicleSearchError", "Nhập giá trị biển số chuẩn hóa để tra cứu.");
            } else {
                try {
                    PagedResponse<VehicleSummary> results = vehicleService.list(plateNumber, 0, 20);
                    model.addAttribute("vehicleResults", results.items());
                    if (results.items().isEmpty()) {
                        model.addAttribute("vehicleSearchError", "Không tìm thấy xe đã ghi nhận. Kiểm tra giá trị chuẩn hóa đã nhập.");
                    }
                } catch (VehicleInvalidRequestException exception) {
                    model.addAttribute("vehicleSearchError", "Giá trị biển số tra cứu chưa hợp lệ.");
                }
            }
        }
        loadSelectedDetails(apartmentId, residentId, authentication, model);
        loadSelectedVehicle(vehicleId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/apartment")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('APARTMENT_MANAGE')")
    public String createApartment(
            @Valid @ModelAttribute("apartmentForm") ApartmentForm form,
            BindingResult bindingResult,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        ApartmentDetail created = null;
        if (bindingResult.hasErrors()) {
            model.addAttribute("apartmentError", "Vui lòng nhập tòa nhà và mã căn hộ hợp lệ.");
        } else {
            try {
                created = apartmentService.create(
                        new ApartmentCreateRequest(form.getBuilding(), form.getApartmentCode(), form.getFloorNo()),
                        actor(account));
                apartmentId = created.id();
                model.addAttribute("apartmentNotice", "Căn hộ đã được lưu.");
            } catch (ApartmentIdentityConflictException exception) {
                model.addAttribute("apartmentError", "Căn hộ này đã tồn tại. Hãy tìm và chọn hồ sơ hiện có.");
            } catch (ApartmentInvalidRequestException exception) {
                model.addAttribute("apartmentError", "Thông tin căn hộ chưa hợp lệ. Vui lòng kiểm tra lại.");
            }
        }
        if (created != null) {
            model.addAttribute("selectedApartment", created);
        }
        preparePostModel(apartmentId, residentId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/resident/lookup")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_READ')")
    public String lookupResident(
            @ModelAttribute("residentForm") ResidentForm form,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        ResidentDetail found = null;
        try {
            found = residentService.lookupByIdentityNumber(
                    new ResidentLookupRequest(form.getIdentityNumber()), actor(account));
            residentId = found.id();
            model.addAttribute("residentNotice", "Đã tìm thấy hồ sơ cư dân.");
        } catch (ResidentNotFoundException exception) {
            model.addAttribute("residentError", "Không tìm thấy hồ sơ cư dân với thông tin đã nhập.");
        } catch (ResidentInvalidRequestException exception) {
            model.addAttribute("residentError", "Vui lòng nhập số định danh hợp lệ để tra cứu.");
        }
        model.addAttribute("residentForm", form);
        if (found != null) {
            model.addAttribute("selectedResident", found);
        }
        preparePostModel(apartmentId, residentId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/resident")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('RESIDENT_MANAGE')")
    public String createOrReuseResident(
            @Valid @ModelAttribute("residentForm") ResidentForm form,
            BindingResult bindingResult,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        ResidentCreateResult result = null;
        if (bindingResult.hasErrors()) {
            model.addAttribute("residentError", "Vui lòng nhập họ và tên cùng số định danh hợp lệ.");
        } else {
            try {
                result = residentService.createOrReuse(new ResidentCreateRequest(
                        form.getFullName(), form.getIdentityNumber(), form.getDateOfBirth(),
                        form.getPhone(), form.getEmail()), actor(account));
                residentId = result.resident().id();
                model.addAttribute("residentNotice", result.created()
                        ? "Hồ sơ cư dân mới đã được tạo."
                        : "Hồ sơ đã tồn tại và được sử dụng lại; thông tin hiện có không bị ghi đè.");
            } catch (ResidentIdentityConflictException exception) {
                model.addAttribute("residentError", "Số định danh đã gắn với hồ sơ khác. Hãy tra cứu hồ sơ hiện có.");
            } catch (ResidentInvalidRequestException exception) {
                model.addAttribute("residentError", "Thông tin cư dân chưa hợp lệ. Vui lòng kiểm tra lại.");
            }
        }
        model.addAttribute("residentForm", form);
        if (result != null) {
            model.addAttribute("selectedResident", result.resident());
        }
        preparePostModel(apartmentId, residentId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/membership/head")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public String assignHouseholdHead(
            @ModelAttribute("membershipForm") MembershipForm form,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        submitMembership(true, form, apartmentId, residentId, account, model);
        preparePostModel(apartmentId, residentId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/membership/member")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('HOUSEHOLD_MEMBERSHIP_MANAGE')")
    public String addHouseholdMember(
            @ModelAttribute("membershipForm") MembershipForm form,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        submitMembership(false, form, apartmentId, residentId, account, model);
        preparePostModel(apartmentId, residentId, authentication, model);
        return VIEW;
    }

    private void submitMembership(
            boolean householdHead,
            MembershipForm form,
            Long apartmentId,
            Long residentId,
            AuthenticatedAccount account,
            Model model) {
        model.addAttribute("membershipForm", form);
        try {
            if (form.getValidFrom() == null || form.getValidFrom().isBlank()) {
                throw new MembershipInvalidRequestException();
            }
            LocalDateTime validFrom = LocalDateTime.parse(form.getValidFrom());
            LocalDateTime validTo = form.getValidTo() == null || form.getValidTo().isBlank()
                    ? null : LocalDateTime.parse(form.getValidTo());
            var saved = householdHead
                    ? membershipService.assignHouseholdHead(apartmentId,
                            new MembershipHeadAssignRequest(residentId, validFrom, validTo, form.getReason()), actor(account))
                    : membershipService.add(new MembershipCreateRequest(
                            apartmentId, residentId, MembershipRole.MEMBER, validFrom, validTo, form.getReason()), actor(account));
            model.addAttribute("apartmentId", saved.apartmentId());
            model.addAttribute("residentId", saved.residentId());
            model.addAttribute("membershipNotice", householdHead
                    ? "Chủ hộ đã được ghi nhận." : "Thành viên đã được thêm vào hộ.");
            model.addAttribute("membershipRole", saved.memberRole());
        } catch (DateTimeParseException | MembershipInvalidRequestException exception) {
            model.addAttribute("membershipError", "Khoảng thời gian hoặc thông tin quan hệ chưa hợp lệ.");
        } catch (MembershipOverlapException exception) {
            model.addAttribute("membershipError", "Cư dân đã có quan hệ hộ trùng khoảng thời gian.");
        } catch (HouseholdHeadConflictException exception) {
            model.addAttribute("membershipError", "Đã có chủ hộ trong khoảng thời gian này.");
        } catch (MembershipStatusConflictException exception) {
            model.addAttribute("membershipError", "Căn hộ hoặc cư dân không ở trạng thái cho phép.");
        } catch (ApartmentNotFoundException | ResidentNotFoundException exception) {
            model.addAttribute("membershipError", "Không tìm thấy căn hộ hoặc cư dân đã chọn.");
        }
    }

    @PostMapping("/management/resident-registration/vehicle/owner")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public String assignVehicleOwner(
            @ModelAttribute("ownerForm") OwnerForm form,
            @RequestParam(name = "vehicleId", required = false) Long vehicleId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "plateNumber", required = false) String plateNumber,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        model.addAttribute("ownerForm", form);
        try {
            if (form.getValidFrom() == null || form.getValidFrom().isBlank()) {
                throw new VehicleInvalidRequestException();
            }
            LocalDateTime validFrom = LocalDateTime.parse(form.getValidFrom());
            LocalDateTime validTo = form.getValidTo() == null || form.getValidTo().isBlank()
                    ? null : LocalDateTime.parse(form.getValidTo());
            VehicleRightDetail assigned = vehicleService.assignOwner(vehicleId,
                    new VehicleOwnerAssignmentRequest(residentId, validFrom, validTo, form.getReason()), actor(account));
            vehicleId = assigned.vehicleId();
            residentId = assigned.residentId();
            model.addAttribute("ownerNotice", "Quan hệ chủ xe đã được ghi nhận.");
            model.addAttribute("ownerRelationType", assigned.relationType());
        } catch (DateTimeParseException | VehicleInvalidRequestException exception) {
            model.addAttribute("ownerError", "Thông tin chủ xe hoặc khoảng thời gian chưa hợp lệ.");
        } catch (VehicleOwnerConflictException exception) {
            model.addAttribute("ownerError", "Đã có chủ xe trong khoảng thời gian này.");
        } catch (VehicleRightOverlapException exception) {
            model.addAttribute("ownerError", "Cư dân đã có quan hệ với xe trùng khoảng thời gian.");
        } catch (VehicleStatusConflictException exception) {
            model.addAttribute("ownerError", "Cư dân không ở trạng thái cho phép gán chủ xe.");
        } catch (VehicleNotFoundException exception) {
            model.addAttribute("ownerError", "Không tìm thấy xe hoặc cư dân đã chọn.");
        }
        model.addAttribute("vehicleSearchForm", new VehicleSearchForm(plateNumber));
        model.addAttribute("apartmentId", apartmentId);
        model.addAttribute("residentId", residentId);
        model.addAttribute("vehicleId", vehicleId);
        preparePostModel(apartmentId, residentId, authentication, model);
        loadSelectedVehicle(vehicleId, authentication, model);
        return VIEW;
    }

    @PostMapping("/management/resident-registration/vehicle/authorized-user")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('VEHICLE_RIGHT_MANAGE')")
    public String grantAuthorizedUser(
            @ModelAttribute("authorizedUserForm") AuthorizedUserForm form,
            @RequestParam(name = "vehicleId", required = false) Long vehicleId,
            @RequestParam(name = "residentId", required = false) Long residentId,
            @RequestParam(name = "apartmentId", required = false) Long apartmentId,
            @RequestParam(name = "plateNumber", required = false) String plateNumber,
            Authentication authentication,
            @AuthenticationPrincipal AuthenticatedAccount account,
            Model model) {
        model.addAttribute("authorizedUserForm", form);
        try {
            if (form.getGuarantorType() == null || form.getGuarantorResidentId() == null
                    || form.getGuarantorResidentId().isBlank()
                    || form.getValidFrom() == null || form.getValidFrom().isBlank()) {
                throw new VehicleInvalidRequestException();
            }
            LocalDateTime validFrom = LocalDateTime.parse(form.getValidFrom());
            LocalDateTime validTo = form.getValidTo() == null || form.getValidTo().isBlank()
                    ? null : LocalDateTime.parse(form.getValidTo());
            Long guarantorResidentId = Long.valueOf(form.getGuarantorResidentId());
            Long guarantorApartmentId = form.getGuarantorApartmentId() == null
                    || form.getGuarantorApartmentId().isBlank()
                            ? null : Long.valueOf(form.getGuarantorApartmentId());
            VehicleRightDetail grant = authorizedUserService.grantAuthorizedUser(vehicleId,
                    new AuthorizedUserGrantRequest(vehicleId, residentId, form.getGuarantorType(),
                            guarantorResidentId, guarantorApartmentId, validFrom, validTo, form.getReason()),
                    actor(account));
            vehicleId = grant.vehicleId();
            residentId = grant.residentId();
            model.addAttribute("authorizedUserNotice", "Quyền sử dụng xe đã được ghi nhận.");
            model.addAttribute("authorizedUserRelationType", grant.relationType());
            model.addAttribute("authorizedUserGuarantorType", grant.guarantorType());
        } catch (DateTimeParseException | NumberFormatException | VehicleInvalidRequestException exception) {
            model.addAttribute("authorizedUserError", "Thông tin quyền sử dụng hoặc khoảng thời gian chưa hợp lệ.");
        } catch (GuarantorChainConflictException exception) {
            model.addAttribute("authorizedUserError", "Không thể xác nhận chuỗi bảo lãnh cho toàn bộ khoảng thời gian đã nhập.");
        } catch (VehicleRightOverlapException exception) {
            model.addAttribute("authorizedUserError", "Khoảng thời gian quyền sử dụng bị trùng với quan hệ hiện có.");
        } catch (VehicleOwnerConflictException exception) {
            model.addAttribute("authorizedUserError", "Có nhiều quan hệ chủ xe chồng lấn; hãy kiểm tra hồ sơ xe.");
        } catch (VehicleStatusConflictException exception) {
            model.addAttribute("authorizedUserError", "Cư dân hoặc căn hộ không ở trạng thái cho phép.");
        } catch (VehicleNotFoundException exception) {
            model.addAttribute("authorizedUserError", "Không tìm thấy xe, cư dân hoặc căn hộ đã chọn.");
        } catch (VehicleConcurrentModificationException | ConcurrencyFailureException exception) {
            model.addAttribute("authorizedUserError", "Quan hệ vừa thay đổi đồng thời. Kiểm tra lại hồ sơ rồi gửi lại.");
        }
        model.addAttribute("vehicleSearchForm", new VehicleSearchForm(plateNumber));
        model.addAttribute("apartmentId", apartmentId);
        model.addAttribute("residentId", residentId);
        model.addAttribute("vehicleId", vehicleId);
        preparePostModel(apartmentId, residentId, authentication, model);
        loadSelectedVehicle(vehicleId, authentication, model);
        return VIEW;
    }

    private void preparePostModel(Long apartmentId, Long residentId, Authentication authentication, Model model) {
        model.addAttribute("apartmentForm", model.getAttribute("apartmentForm") == null
                ? new ApartmentForm() : model.getAttribute("apartmentForm"));
        model.addAttribute("residentForm", model.getAttribute("residentForm") == null
                ? new ResidentForm() : model.getAttribute("residentForm"));
        model.addAttribute("vehicleSearchForm", model.getAttribute("vehicleSearchForm") == null
                ? new VehicleSearchForm() : model.getAttribute("vehicleSearchForm"));
        model.addAttribute("apartmentId", apartmentId);
        model.addAttribute("residentId", residentId);
        setCapabilities(model, authentication);
        loadSelectedDetails(apartmentId, residentId, authentication, model);
    }

    private void loadSelectedDetails(
            Long apartmentId,
            Long residentId,
            Authentication authentication,
            Model target) {
        if (apartmentId != null && apartmentId > 0 && hasAuthority(authentication, "APARTMENT_READ")
                && target.getAttribute("selectedApartment") == null) {
            try {
                target.addAttribute("selectedApartment", apartmentService.detail(apartmentId, actor(authentication)));
            } catch (ApartmentNotFoundException | ApartmentInvalidRequestException exception) {
                target.addAttribute("apartmentError", "Không tìm thấy căn hộ đã chọn.");
                target.addAttribute("apartmentId", null);
            }
        }
        if (residentId != null && residentId > 0 && hasAuthority(authentication, "RESIDENT_READ")
                && target.getAttribute("selectedResident") == null) {
            try {
                target.addAttribute("selectedResident", residentService.detail(residentId, actor(authentication)));
            } catch (ResidentNotFoundException | ResidentInvalidRequestException exception) {
                target.addAttribute("residentError", "Không tìm thấy cư dân đã chọn.");
                target.addAttribute("residentId", null);
            }
        }
    }

    private void loadSelectedVehicle(Long vehicleId, Authentication authentication, Model target) {
        if (vehicleId != null && vehicleId > 0 && hasAuthority(authentication, "VEHICLE_RIGHT_READ")
                && target.getAttribute("selectedVehicle") == null) {
            try {
                target.addAttribute("selectedVehicle", vehicleService.detail(vehicleId, actor(authentication)));
            } catch (VehicleNotFoundException | VehicleInvalidRequestException exception) {
                target.addAttribute("vehicleError", "Không tìm thấy xe đã chọn. Hãy tra cứu xe hiện có.");
                target.addAttribute("vehicleId", null);
            }
        }
    }

    private User actor(AuthenticatedAccount account) {
        return account == null ? null : userRepository.findById(account.userId()).orElse(null);
    }

    private User actor(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof AuthenticatedAccount account ? actor(account) : null;
    }

    private void setCapabilities(Model model, Authentication authentication) {
        model.addAttribute("canReadApartments", hasAuthority(authentication, "APARTMENT_READ"));
        model.addAttribute("canManageApartments", hasAuthority(authentication, "APARTMENT_MANAGE"));
        model.addAttribute("canReadResidents", hasAuthority(authentication, "RESIDENT_READ"));
        model.addAttribute("canManageResidents", hasAuthority(authentication, "RESIDENT_MANAGE"));
        model.addAttribute("canManageMemberships", hasAuthority(authentication, "HOUSEHOLD_MEMBERSHIP_MANAGE"));
        model.addAttribute("canReadVehicles", hasAuthority(authentication, "VEHICLE_RIGHT_READ"));
        model.addAttribute("canManageVehicleRights", hasAuthority(authentication, "VEHICLE_RIGHT_MANAGE"));
    }

    private boolean hasAuthority(Authentication authentication, String code) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(code::equals);
    }

    public static class ApartmentForm {
        @NotBlank(message = "Vui lòng nhập tòa nhà")
        @Size(max = 100)
        private String building;

        @NotBlank(message = "Vui lòng nhập mã căn hộ")
        @Size(max = 50)
        private String apartmentCode;

        private Integer floorNo;

        public ApartmentForm() {}

        public ApartmentForm(String building, String apartmentCode, Integer floorNo) {
            this.building = building;
            this.apartmentCode = apartmentCode;
            this.floorNo = floorNo;
        }

        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getApartmentCode() { return apartmentCode; }
        public void setApartmentCode(String apartmentCode) { this.apartmentCode = apartmentCode; }
        public Integer getFloorNo() { return floorNo; }
        public void setFloorNo(Integer floorNo) { this.floorNo = floorNo; }
    }

    public static class ResidentForm {
        @NotBlank(message = "Vui lòng nhập họ và tên")
        @Size(max = 150)
        private String fullName;

        @NotBlank(message = "Vui lòng nhập số định danh")
        @Size(max = 30)
        private String identityNumber;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateOfBirth;

        @Size(max = 30)
        private String phone;

        @Size(max = 150)
        private String email;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getIdentityNumber() { return identityNumber; }
        public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class MembershipForm {
        private String validFrom;
        private String validTo;
        private String reason;

        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
        public String getValidTo() { return validTo; }
        public void setValidTo(String validTo) { this.validTo = validTo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class VehicleSearchForm {
        private String plateNumber;

        public VehicleSearchForm() {}
        public VehicleSearchForm(String plateNumber) { this.plateNumber = plateNumber; }
        public String getPlateNumber() { return plateNumber; }
        public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    }

    public static class OwnerForm {
        private String validFrom;
        private String validTo;
        private String reason;

        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
        public String getValidTo() { return validTo; }
        public void setValidTo(String validTo) { this.validTo = validTo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AuthorizedUserForm {
        private VehicleRelationGuarantorType guarantorType;
        private String guarantorResidentId;
        private String guarantorApartmentId;
        private String validFrom;
        private String validTo;
        private String reason;

        public VehicleRelationGuarantorType getGuarantorType() { return guarantorType; }
        public void setGuarantorType(VehicleRelationGuarantorType guarantorType) { this.guarantorType = guarantorType; }
        public String getGuarantorResidentId() { return guarantorResidentId; }
        public void setGuarantorResidentId(String guarantorResidentId) { this.guarantorResidentId = guarantorResidentId; }
        public String getGuarantorApartmentId() { return guarantorApartmentId; }
        public void setGuarantorApartmentId(String guarantorApartmentId) { this.guarantorApartmentId = guarantorApartmentId; }
        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
        public String getValidTo() { return validTo; }
        public void setValidTo(String validTo) { this.validTo = validTo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
