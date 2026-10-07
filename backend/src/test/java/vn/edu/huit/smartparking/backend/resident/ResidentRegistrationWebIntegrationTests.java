package vn.edu.huit.smartparking.backend.resident;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusManagementService;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleStatus;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleManagementService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResidentRegistrationWebIntegrationTests {
    private static final String REGISTRATION_URL = "/management/resident-registration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResidentManagementService residentService;

    @Autowired
    private ApartmentManagementService apartmentService;

    @Autowired
    private ApartmentMembershipManagementService membershipService;

    @Autowired
    private ResidentStatusManagementService residentStatusService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleManagementService vehicleService;

    @Test
    void apartmentCreateRequiresManagementPermissionAndCsrf() throws Exception {
        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "APARTMENT_READ"))
                        .with(csrf())
                        .param("building", "Forbidden")
                        .param("apartmentCode", "A-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "APARTMENT_MANAGE"))
                        .param("building", "Missing CSRF")
                        .param("apartmentCode", "A-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsApartmentThenFindsItByExactIdentity() throws Exception {
        String building = "Web apartment " + UUID.randomUUID();

        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(
                                () -> "ROLE_MANAGEMENT", () -> "APARTMENT_MANAGE", () -> "APARTMENT_READ"))
                        .with(csrf())
                        .param("building", building)
                        .param("apartmentCode", "A-01")
                        .param("floorNo", "8"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/resident-registration"))
                .andExpect(content().string(containsString("Căn hộ đã được lưu")))
                .andExpect(content().string(containsString(building)))
                .andExpect(content().string(containsString("A-01")))
                .andExpect(content().string(containsString("name=\"apartmentId\" value=\"")));

        mockMvc.perform(get(REGISTRATION_URL)
                        .with(user("manager").authorities(
                                () -> "ROLE_MANAGEMENT", () -> "APARTMENT_READ", () -> "APARTMENT_MANAGE"))
                        .param("searchApartment", "true")
                        .param("building", building)
                        .param("apartmentCode", "A-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A-01")));
    }

    @Test
    void apartmentConflictKeepsEnteredValuesAndShowsActionableFeedback() throws Exception {
        String building = "Web duplicate " + UUID.randomUUID();
        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "APARTMENT_MANAGE"))
                        .with(csrf())
                        .param("building", building)
                        .param("apartmentCode", "A-02"))
                .andExpect(status().isOk());

        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "APARTMENT_MANAGE"))
                        .with(csrf())
                        .param("building", "  " + building.toUpperCase() + "  ")
                        .param("apartmentCode", "a-02"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Căn hộ này đã tồn tại")))
                .andExpect(content().string(containsString("  " + building.toUpperCase() + "  ")))
                .andExpect(content().string(containsString("a-02")));
    }

    @Test
    void residentLookupUsesPersistedExactLookupHitAndMissWithoutExposingIdentityNumber() throws Exception {
        String identityNumber = "WEB-" + UUID.randomUUID().toString().substring(0, 16);
        residentService.createOrReuse(
                new ResidentCreateRequest("Persisted Web Resident", identityNumber, null, null, null), null);

        mockMvc.perform(post(REGISTRATION_URL + "/resident/lookup")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "RESIDENT_READ"))
                        .with(csrf())
                        .param("identityNumber", identityNumber))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã tìm thấy hồ sơ cư dân")))
                .andExpect(content().string(containsString("Persisted Web Resident")));

        String missingIdentity = "MISS-" + UUID.randomUUID().toString().substring(0, 16);
        mockMvc.perform(post(REGISTRATION_URL + "/resident/lookup")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "RESIDENT_READ"))
                        .with(csrf())
                        .param("identityNumber", missingIdentity))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy hồ sơ cư dân")))
                .andExpect(content().string(containsString(missingIdentity)));
    }

    @Test
    void residentCreateDistinguishesCreatedFromReusedAndValidationRetainsInput() throws Exception {
        String identityNumber = "CREATE-" + UUID.randomUUID().toString().substring(0, 16);
        mockMvc.perform(post(REGISTRATION_URL + "/resident")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "RESIDENT_MANAGE"))
                        .with(csrf())
                        .param("fullName", "Original Resident")
                        .param("identityNumber", identityNumber))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hồ sơ cư dân mới đã được tạo")))
                .andExpect(content().string(containsString("Original Resident")));

        mockMvc.perform(post(REGISTRATION_URL + "/resident")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "RESIDENT_MANAGE"))
                        .with(csrf())
                        .param("fullName", "Submitted Replacement Name")
                        .param("identityNumber", identityNumber))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hồ sơ đã tồn tại và được sử dụng lại; thông tin hiện có không bị ghi đè")))
                .andExpect(content().string(containsString("Original Resident")))
                .andExpect(content().string(containsString("value=\"Submitted Replacement Name\"")));

        org.junit.jupiter.api.Assertions.assertEquals("Original Resident",
                residentService.lookupByIdentityNumber(new ResidentLookupRequest(identityNumber), null).fullName());

        mockMvc.perform(post(REGISTRATION_URL + "/resident")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "RESIDENT_MANAGE"))
                        .with(csrf())
                        .param("fullName", "")
                        .param("identityNumber", "PRESERVE-THIS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập họ và tên")))
                .andExpect(content().string(containsString("PRESERVE-THIS")));
    }

    @Test
    void apartmentFieldValidationIsLinkedToItsControl() throws Exception {
        mockMvc.perform(post(REGISTRATION_URL + "/apartment")
                        .with(user("manager").authorities(
                                () -> "ROLE_MANAGEMENT", () -> "APARTMENT_MANAGE", () -> "APARTMENT_READ"))
                        .with(csrf())
                        .param("building", "B".repeat(101))
                        .param("apartmentCode", "A-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("aria-describedby=\"apartment-building-error\"")))
                .andExpect(content().string(containsString("id=\"apartment-building-error\"")))
                .andExpect(content().string(containsString("data-focus-on-load")));
    }

    @Test
    void assignsHouseholdHeadAndAddsMemberAsDistinctOperations() throws Exception {
        Long apartmentId = createApartment();
        Long headResidentId = createResident("Web household head");
        Long memberResidentId = createResident("Web household member");

        membershipPost("/membership/head", apartmentId, headResidentId,
                "2030-10-05T09:00", "2030-10-05T11:00", "Head registration")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chủ hộ đã được ghi nhận")))
                .andExpect(content().string(containsString("HOUSEHOLD_HEAD")))
                .andExpect(content().string(containsString("Gán vai trò chủ hộ")))
                .andExpect(content().string(containsString("Thêm vai trò thành viên")))
                .andExpect(content().string(containsString("name=\"apartmentId\" value=\"" + apartmentId + "\"")))
                .andExpect(content().string(containsString("name=\"residentId\" value=\"" + headResidentId + "\"")));

        membershipPost("/membership/member", apartmentId, memberResidentId,
                "2030-10-05T09:00", "2030-10-05T11:00", "Member registration")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thành viên đã được thêm vào hộ")))
                .andExpect(content().string(containsString("MEMBER")))
                .andExpect(content().string(containsString("Member registration")));
    }

    @Test
    void membershipConflictsShowRecoverableFeedbackAndRetainIntervalAndReason() throws Exception {
        Long apartmentId = createApartment();
        Long currentHeadId = createResident("Existing household head");
        Long secondHeadId = createResident("Second household head");
        membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(currentHeadId, start(), end(), "Existing head"), null);

        membershipPost("/membership/head", apartmentId, secondHeadId,
                "2030-10-05T09:00", "2030-10-05T11:00", "Preserve head conflict")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã có chủ hộ trong khoảng thời gian này")))
                .andExpect(content().string(containsString("value=\"2030-10-05T09:00\"")))
                .andExpect(content().string(containsString("value=\"Preserve head conflict\"")));

        Long existingMemberId = createResident("Existing household member");
        membershipService.add(new MembershipCreateRequest(apartmentId, existingMemberId, MembershipRole.MEMBER,
                start(), end(), "Existing member"), null);

        membershipPost("/membership/member", apartmentId, existingMemberId,
                "2030-10-05T09:00", "2030-10-05T11:00", "Preserve member overlap")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cư dân đã có quan hệ hộ trùng khoảng thời gian")))
                .andExpect(content().string(containsString("value=\"Preserve member overlap\"")));
    }

    @Test
    void inactiveResidentAndInvalidIntervalArePresentedWithoutLosingSelection() throws Exception {
        Long apartmentId = createApartment();
        Long residentId = createResident("Inactive household resident");
        residentStatusService.changeStatus(residentId,
                new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Test inactive resident", null, null), null);

        membershipPost("/membership/member", apartmentId, residentId,
                "2030-10-05T09:00", "2030-10-05T11:00", "Inactive membership attempt")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Căn hộ hoặc cư dân không ở trạng thái cho phép")))
                .andExpect(content().string(containsString("name=\"apartmentId\" value=\"" + apartmentId + "\"")))
                .andExpect(content().string(containsString("value=\"Inactive membership attempt\"")));

        Long activeResidentId = createResident("Invalid interval resident");
        membershipPost("/membership/member", apartmentId, activeResidentId,
                "2030-10-05T11:00", "2030-10-05T09:00", "Retained invalid interval")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khoảng thời gian hoặc thông tin quan hệ chưa hợp lệ")))
                .andExpect(content().string(containsString("value=\"2030-10-05T11:00\"")))
                .andExpect(content().string(containsString("value=\"Retained invalid interval\"")));
    }

    @Test
    void vehicleLookupUsesExactStoredNormalizedPlateAndKeepsResidentSelection() throws Exception {
        Vehicle vehicle = createVehicle("WEB-PLATE-" + UUID.randomUUID().toString().substring(0, 8),
                "WEBPLATE" + UUID.randomUUID().toString().substring(0, 8));
        Long residentId = createResident("Vehicle lookup resident");

        vehicleSearch(vehicle.getPlateNormalized(), residentId)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(vehicle.getPlateNumber())))
                .andExpect(content().string(containsString("vehicleId=" + vehicle.getId())))
                .andExpect(content().string(containsString("name=\"residentId\" value=\"" + residentId + "\"")))
                .andExpect(content().string(containsString("khớp chính xác")))
                .andExpect(content().string(not(containsString("Tạo hồ sơ xe"))));

        mockMvc.perform(get(REGISTRATION_URL)
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT",
                                () -> "VEHICLE_RIGHT_READ", () -> "VEHICLE_RIGHT_MANAGE", () -> "RESIDENT_READ"))
                        .param("vehicleId", vehicle.getId().toString())
                        .param("residentId", residentId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(vehicle.getPlateNumber())))
                .andExpect(content().string(containsString("OWNER")))
                .andExpect(content().string(containsString("name=\"vehicleId\" value=\"" + vehicle.getId() + "\"")));

        vehicleSearch(vehicle.getPlateNormalized() + "-MISSING", residentId)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy xe đã ghi nhận")))
                .andExpect(content().string(containsString(vehicle.getPlateNormalized() + "-MISSING")))
                .andExpect(content().string(containsString("name=\"residentId\" value=\"" + residentId + "\"")));
    }

    @Test
    void assignsOwnerToSelectedVehicleAndResidentWithoutCreatingVehicle() throws Exception {
        Vehicle vehicle = createVehicle("WEB-OWNER-" + UUID.randomUUID().toString().substring(0, 8),
                "WEBOWNER" + UUID.randomUUID().toString().substring(0, 8));
        Long residentId = createResident("Vehicle owner resident");

        ownerPost(vehicle.getId(), residentId, "2030-10-05T09:00", "2030-10-05T11:00", "Owner registration")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quan hệ chủ xe đã được ghi nhận")))
                .andExpect(content().string(containsString("OWNER")))
                .andExpect(content().string(containsString(vehicle.getPlateNumber())))
                .andExpect(content().string(containsString("Owner registration")));
    }

    @Test
    void ownerAssignmentRequiresManagementPermissionAndCsrf() throws Exception {
        mockMvc.perform(post(REGISTRATION_URL + "/vehicle/owner")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_READ"))
                        .with(csrf())
                        .param("vehicleId", "1")
                        .param("residentId", "1")
                        .param("validFrom", "2030-10-05T09:00")
                        .param("reason", "Unauthorized attempt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(REGISTRATION_URL + "/vehicle/owner")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_MANAGE"))
                        .param("vehicleId", "1")
                        .param("residentId", "1")
                        .param("validFrom", "2030-10-05T09:00")
                        .param("reason", "Missing CSRF"))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantsAuthorizedUserWithExplicitOwnerOrHouseholdHeadGuarantor() throws Exception {
        Vehicle vehicle = createVehicle("WEB-GRANT-" + UUID.randomUUID().toString().substring(0, 8),
                "WEBGRANT" + UUID.randomUUID().toString().substring(0, 8));
        Long ownerId = createResident("Authorized user owner guarantor");
        Long authorizedResidentId = createResident("Authorized user target");
        vehicleService.assignOwner(vehicle.getId(), new VehicleOwnerAssignmentRequest(
                ownerId, start(), null, "Owner source"), null);

        authorizedUserPost(vehicle.getId(), authorizedResidentId, "OWNER", ownerId, null,
                "2030-10-05T10:00", "2030-10-05T11:00", "OWNER guarantor grant")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quyền sử dụng xe đã được ghi nhận")))
                .andExpect(content().string(containsString("AUTHORIZED_USER")))
                .andExpect(content().string(containsString("OWNER")))
                .andExpect(content().string(containsString("OWNER guarantor grant")))
                .andExpect(content().string(containsString("authorized-user-guarantor-resident")))
                .andExpect(content().string(containsString("vehicleId\" value=\"" + vehicle.getId())))
                .andExpect(content().string(containsString("Không yêu cầu xác minh khuôn mặt")))
                .andExpect(content().string(containsString(vehicle.getPlateNumber())));

        Long headId = createResident("Authorized user household head");
        Long householdTargetId = createResident("Authorized user household target");
        Long apartmentId = createApartment();
        membershipService.add(new MembershipCreateRequest(apartmentId, ownerId, MembershipRole.MEMBER,
                start(), null, "Owner membership"), null);
        membershipService.assignHouseholdHead(apartmentId,
                new MembershipHeadAssignRequest(headId, start(), null, "Head membership"), null);

        authorizedUserPost(vehicle.getId(), householdTargetId, "HOUSEHOLD_HEAD", headId, apartmentId,
                "2030-10-05T10:00", "2030-10-05T11:00", "Household head grant")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quyền sử dụng xe đã được ghi nhận")))
                .andExpect(content().string(containsString("HOUSEHOLD_HEAD")))
                .andExpect(content().string(containsString("Household head grant")));
    }

    @Test
    void authorizedUserGrantShowsRecoverableChainOverlapStatusNotFoundAndInvalidErrors() throws Exception {
        Vehicle vehicle = createVehicle("WEB-GRANT-ERROR-" + UUID.randomUUID().toString().substring(0, 6),
                "WEBGRANTERR" + UUID.randomUUID().toString().substring(0, 6));
        Long ownerId = createResident("Grant error owner");
        Long targetId = createResident("Grant error target");
        Long otherResidentId = createResident("Grant error other guarantor");
        vehicleService.assignOwner(vehicle.getId(), new VehicleOwnerAssignmentRequest(
                ownerId, start(), null, "Grant error owner source"), null);

        authorizedUserPost(vehicle.getId(), targetId, "OWNER", otherResidentId, null,
                "2030-10-05T10:00", "2030-10-05T11:00", "Retain chain reason")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không thể xác nhận chuỗi bảo lãnh")))
                .andExpect(content().string(containsString("value=\"Retain chain reason\"")));

        authorizedUserPost(vehicle.getId(), targetId, "OWNER", ownerId, null,
                "2030-10-05T10:00", "2030-10-05T11:00", "First target grant")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quyền sử dụng xe đã được ghi nhận")));
        authorizedUserPost(vehicle.getId(), targetId, "OWNER", ownerId, null,
                "2030-10-05T10:30", "2030-10-05T11:30", "Retain overlap reason")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khoảng thời gian quyền sử dụng bị trùng")))
                .andExpect(content().string(containsString("value=\"Retain overlap reason\"")));

        Long inactiveTargetId = createResident("Inactive grant target");
        residentStatusService.changeStatus(inactiveTargetId,
                new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Inactive grant target", null, null), null);
        authorizedUserPost(vehicle.getId(), inactiveTargetId, "OWNER", ownerId, null,
                "2030-10-05T12:00", "2030-10-05T13:00", "Retain status reason")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cư dân hoặc căn hộ không ở trạng thái cho phép")))
                .andExpect(content().string(containsString("value=\"Retain status reason\"")));

        authorizedUserPost(vehicle.getId(), targetId, "OWNER", ownerId, null,
                "2030-10-05T14:00", "2030-10-05T13:00", "Retain invalid grant")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin quyền sử dụng hoặc khoảng thời gian chưa hợp lệ")))
                .andExpect(content().string(containsString("value=\"Retain invalid grant\"")));

        mockMvc.perform(post(REGISTRATION_URL + "/vehicle/authorized-user")
                        .with(user("manager").authorities(
                                () -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_MANAGE",
                                () -> "VEHICLE_RIGHT_READ", () -> "RESIDENT_READ"))
                        .with(csrf())
                        .param("vehicleId", vehicle.getId().toString())
                        .param("residentId", targetId.toString())
                        .param("guarantorType", "OWNER")
                        .param("guarantorResidentId", "not-a-resident-id")
                        .param("validFrom", "2030-10-05T14:00")
                        .param("reason", "Retain malformed guarantor"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin quyền sử dụng hoặc khoảng thời gian chưa hợp lệ")))
                .andExpect(content().string(containsString("value=\"not-a-resident-id\"")))
                .andExpect(content().string(containsString("value=\"Retain malformed guarantor\"")));

        authorizedUserPost(999999999L, targetId, "OWNER", ownerId, null,
                "2030-10-05T14:00", "2030-10-05T15:00", "Retain missing grant")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy xe, cư dân hoặc căn hộ đã chọn")))
                .andExpect(content().string(containsString("value=\"Retain missing grant\"")));
    }

    @Test
    void authorizedUserGrantRequiresManagementPermissionAndCsrf() throws Exception {
        mockMvc.perform(post(REGISTRATION_URL + "/vehicle/authorized-user")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_READ"))
                        .with(csrf()).param("vehicleId", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(REGISTRATION_URL + "/vehicle/authorized-user")
                        .with(user("manager").authorities(() -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_MANAGE"))
                        .param("vehicleId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerAssignmentShowsVehicleNotFoundOwnerConflictStatusAndInvalidRequest() throws Exception {
        Vehicle vehicle = createVehicle("WEB-CONFLICT-" + UUID.randomUUID().toString().substring(0, 8),
                "WEBCONFLICT" + UUID.randomUUID().toString().substring(0, 8));
        Long currentOwnerId = createResident("Current vehicle owner");
        Long secondResidentId = createResident("Second vehicle owner");
        vehicleService.assignOwner(vehicle.getId(), new VehicleOwnerAssignmentRequest(
                currentOwnerId, start(), end(), "Existing OWNER"), null);

        ownerPost(vehicle.getId(), secondResidentId, "2030-10-05T09:00", "2030-10-05T11:00", "Keep owner conflict")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã có chủ xe trong khoảng thời gian này")))
                .andExpect(content().string(containsString("value=\"Keep owner conflict\"")));

        Long inactiveResidentId = createResident("Inactive OWNER resident");
        residentStatusService.changeStatus(inactiveResidentId,
                new ResidentStatusChangeRequest(ResidentStatus.INACTIVE, "Test inactive resident", null, null), null);
        ownerPost(vehicle.getId(), inactiveResidentId,
                "2030-10-05T11:00", "2030-10-05T12:00", "Keep inactive target")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cư dân không ở trạng thái cho phép gán chủ xe")))
                .andExpect(content().string(containsString("value=\"Keep inactive target\"")));

        ownerPost(vehicle.getId(), secondResidentId,
                "2030-10-05T12:00", "2030-10-05T11:00", "Keep invalid interval")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin chủ xe hoặc khoảng thời gian chưa hợp lệ")))
                .andExpect(content().string(containsString("value=\"2030-10-05T12:00\"")))
                .andExpect(content().string(containsString("value=\"Keep invalid interval\"")));

        ownerPost(999999999L, secondResidentId,
                "2030-10-05T12:00", "2030-10-05T13:00", "Keep missing vehicle")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy xe đã chọn")))
                .andExpect(content().string(containsString("Keep missing vehicle")));
    }

    private org.springframework.test.web.servlet.ResultActions membershipPost(
            String action,
            Long apartmentId,
            Long residentId,
            String validFrom,
            String validTo,
            String reason) throws Exception {
        return mockMvc.perform(post(REGISTRATION_URL + action)
                .with(user("manager").authorities(
                        () -> "ROLE_MANAGEMENT", () -> "HOUSEHOLD_MEMBERSHIP_MANAGE",
                        () -> "APARTMENT_READ", () -> "RESIDENT_READ"))
                .with(csrf())
                .param("apartmentId", apartmentId.toString())
                .param("residentId", residentId.toString())
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("reason", reason));
    }

    private org.springframework.test.web.servlet.ResultActions vehicleSearch(String plateNumber, Long residentId)
            throws Exception {
        return mockMvc.perform(get(REGISTRATION_URL)
                .with(user("manager").authorities(
                        () -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_READ",
                        () -> "VEHICLE_RIGHT_MANAGE", () -> "RESIDENT_READ"))
                .param("searchVehicle", "true")
                .param("plateNumber", plateNumber)
                .param("residentId", residentId.toString()));
    }

    private org.springframework.test.web.servlet.ResultActions ownerPost(
            Long vehicleId,
            Long residentId,
            String validFrom,
            String validTo,
            String reason) throws Exception {
        return mockMvc.perform(post(REGISTRATION_URL + "/vehicle/owner")
                .with(user("manager").authorities(
                        () -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_MANAGE",
                        () -> "VEHICLE_RIGHT_READ", () -> "RESIDENT_READ"))
                .with(csrf())
                .param("vehicleId", vehicleId.toString())
                .param("residentId", residentId.toString())
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("reason", reason));
    }

    private org.springframework.test.web.servlet.ResultActions authorizedUserPost(
            Long vehicleId,
            Long residentId,
            String guarantorType,
            Long guarantorResidentId,
            Long guarantorApartmentId,
            String validFrom,
            String validTo,
            String reason) throws Exception {
        var request = post(REGISTRATION_URL + "/vehicle/authorized-user")
                .with(user("manager").authorities(
                        () -> "ROLE_MANAGEMENT", () -> "VEHICLE_RIGHT_MANAGE",
                        () -> "VEHICLE_RIGHT_READ", () -> "RESIDENT_READ"))
                .with(csrf())
                .param("vehicleId", vehicleId.toString())
                .param("residentId", residentId.toString())
                .param("guarantorType", guarantorType)
                .param("guarantorResidentId", guarantorResidentId.toString())
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("reason", reason);
        if (guarantorApartmentId != null) {
            request.param("guarantorApartmentId", guarantorApartmentId.toString());
        }
        return mockMvc.perform(request);
    }

    private Vehicle createVehicle(String plateNumber, String plateNormalized) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plateNumber);
        vehicle.setPlateNormalized(plateNormalized);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setCreatedAt(LocalDateTime.now());
        vehicle.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.save(vehicle);
    }

    private Long createApartment() {
        return apartmentService.create(new ApartmentCreateRequest(
                "Web membership " + UUID.randomUUID(), "A-01", null), null).id();
    }

    private Long createResident(String fullName) {
        return residentService.createOrReuse(new ResidentCreateRequest(
                fullName, "MEM-" + UUID.randomUUID().toString().substring(0, 16), null, null, null), null)
                .resident().id();
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2030, 10, 5, 9, 0);
    }

    private LocalDateTime end() {
        return LocalDateTime.of(2030, 10, 5, 11, 0);
    }
}
