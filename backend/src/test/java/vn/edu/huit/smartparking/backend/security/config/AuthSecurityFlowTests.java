package vn.edu.huit.smartparking.backend.security.config;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.controller.AccountSecurityPageController;
import vn.edu.huit.smartparking.backend.security.controller.AuthController;
import vn.edu.huit.smartparking.backend.security.controller.LoginPageController;
import vn.edu.huit.smartparking.backend.security.controller.ManagementHomeController;
import vn.edu.huit.smartparking.backend.resident.controller.ApartmentManagementController;
import vn.edu.huit.smartparking.backend.resident.controller.ApartmentMembershipManagementController;
import vn.edu.huit.smartparking.backend.resident.controller.ResidentManagementController;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDeactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentReactivationRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ApartmentSummary;
import vn.edu.huit.smartparking.backend.resident.dto.HistoryItem;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipDetail;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipEndRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipHeadAssignRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipLifecycleRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipTransferRequest;
import vn.edu.huit.smartparking.backend.resident.dto.MembershipVoidRequest;
import vn.edu.huit.smartparking.backend.resident.dto.PagedResponse;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCorrectionRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentCreateRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentLookupRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentStatusChangeRequest;
import vn.edu.huit.smartparking.backend.resident.dto.ResidentSummary;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentMembershipManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.HouseholdHeadConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipOverlapException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentConcurrentModificationException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentCreateResult;
import vn.edu.huit.smartparking.backend.resident.service.ResidentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusManagementService;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentNotFoundException;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.security.service.AuthRequestDetails;
import vn.edu.huit.smartparking.backend.security.service.CurrentJwtAuthenticationConverter;
import vn.edu.huit.smartparking.backend.security.service.JwtTokenService;
import vn.edu.huit.smartparking.backend.security.service.LoginThrottledException;
import vn.edu.huit.smartparking.backend.security.service.PasswordChangeService;
import vn.edu.huit.smartparking.backend.security.service.PasswordPolicyViolationException;
import vn.edu.huit.smartparking.backend.security.service.SecurityAccountService;
import vn.edu.huit.smartparking.backend.vehicle.controller.VehicleManagementController;
import vn.edu.huit.smartparking.backend.vehicle.dto.AuthorizedUserGrantRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerAssignmentRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleOwnerTransferRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightDetail;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightLifecycleRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleRightVoidRequest;
import vn.edu.huit.smartparking.backend.vehicle.dto.VehicleSummary;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleStatus;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleManagementService;
import vn.edu.huit.smartparking.backend.vehicle.service.AuthorizedUserGrantService;
import vn.edu.huit.smartparking.backend.vehicle.service.GuarantorChainConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleStatusConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleNotFoundException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleOwnerConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightOverlapException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightLifecycleService;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRelationStateConflictException;

@WebMvcTest({AuthController.class, AccountSecurityPageController.class, LoginPageController.class,
        ManagementHomeController.class, ApartmentManagementController.class, ResidentManagementController.class,
        ApartmentMembershipManagementController.class, VehicleManagementController.class})
@Import({
        SecurityConfiguration.class,
        WebSessionSecurityFilter.class,
        AuthRequestDetailsSource.class,
        WebAuthenticationSuccessHandler.class,
        WebAuthenticationFailureHandler.class,
        WebLogoutAuditHandler.class,
        CurrentJwtAuthenticationConverter.class,
        JwtConfiguration.class,
        JwtTestConfiguration.class
})
class AuthSecurityFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${server.servlet.session.timeout}")
    private Duration configuredIdleTimeout;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private SecurityAccountService accountService;

    @MockitoBean
    private ApartmentManagementService apartmentManagementService;

    @MockitoBean
    private ApartmentStatusManagementService apartmentStatusManagementService;

    @MockitoBean
    private ResidentManagementService residentManagementService;

    @MockitoBean
    private ResidentStatusManagementService residentStatusManagementService;

    @MockitoBean
    private ApartmentMembershipManagementService apartmentMembershipManagementService;

    @MockitoBean
    private VehicleManagementService vehicleManagementService;

    @MockitoBean
    private AuthorizedUserGrantService authorizedUserGrantService;

    @MockitoBean
    private VehicleRightLifecycleService vehicleRightLifecycleService;

    @Test
    void restLoginDoesNotRequireCsrfAndReturnsBearerContract() throws Exception {
        AuthenticatedAccount account = gateStaffAccount();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                account, null, account.authorities());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(jwtTokenService.issue(account)).thenReturn(new JwtTokenService.IssuedJwt("signed", 43_200));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"gate.staff\",\"password\":\"valid-password-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("signed"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(43_200))
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());

        ArgumentCaptor<Authentication> loginRequest = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(loginRequest.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuthRequestDetails.Channel.REST,
                ((AuthRequestDetails) loginRequest.getValue().getDetails()).channel());
    }

    @Test
    void apartmentListRequiresBearerManagementRoleAndReadPermission() throws Exception {
        String path = "/api/management/apartments";
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        AuthenticatedAccount management = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_READ")));
        ApartmentSummary summary = new ApartmentSummary(41L, "Tower A", "A-01", 5, ApartmentStatus.ACTIVE);
        when(apartmentManagementService.list("Tower A", "a-01", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(summary), 0, 20, 1));
        when(accountService.loadUserById(7L)).thenReturn(gateStaffAccount(), management);
        mockMvc.perform(get(path)
                        .param("building", "Tower A")
                        .param("apartment_code", "a-01")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(path)
                        .param("building", "Tower A")
                        .param("apartment_code", "a-01")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].apartment_code").value("A-01"))
                .andExpect(jsonPath("$.items[0].floor_no").value(5))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].created_at").doesNotExist())
                .andExpect(jsonPath("$.items[0].building_key").doesNotExist())
                .andExpect(jsonPath("$.total_items").value(1));
        verify(apartmentManagementService).list("Tower A", "a-01", 0, 20);
    }

    @Test
    void apartmentCreateRequiresManagePermissionAndUsesSnakeCaseContract() throws Exception {
        AuthenticatedAccount readOnly = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_READ")));
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(readOnly, manager);

        mockMvc.perform(post("/api/management/apartments")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"building\":\"Tower A\",\"apartment_code\":\"A-01\",\"floor_no\":5}"))
                .andExpect(status().isForbidden());

        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        ApartmentDetail detail = new ApartmentDetail(41L, "Tower A", "A-01", 5, ApartmentStatus.ACTIVE,
                LocalDateTime.parse("2026-10-02T09:00:00"), LocalDateTime.parse("2026-10-02T09:00:00"));
        when(apartmentManagementService.create(any(ApartmentCreateRequest.class), any(User.class))).thenReturn(detail);

        mockMvc.perform(post("/api/management/apartments")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"building\":\"Tower A\",\"apartment_code\":\"A-01\",\"floor_no\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apartment_code").value("A-01"))
                .andExpect(jsonPath("$.floor_no").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.createdAt").doesNotExist());

        ArgumentCaptor<ApartmentCreateRequest> request = ArgumentCaptor.forClass(ApartmentCreateRequest.class);
        verify(apartmentManagementService).create(request.capture(), any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals("Tower A", request.getValue().building());
        org.junit.jupiter.api.Assertions.assertEquals("A-01", request.getValue().apartmentCode());
    }

    @Test
    void apartmentCorrectionDistinguishesExplicitNullFloorFromOmittedField() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        ApartmentDetail detail = new ApartmentDetail(41L, "Tower A", "A-01", null, ApartmentStatus.ACTIVE,
                LocalDateTime.parse("2026-10-02T09:00:00"), LocalDateTime.parse("2026-10-02T09:05:00"));
        when(apartmentManagementService.correct(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentCorrectionRequest.class), any(User.class))).thenReturn(detail);

        mockMvc.perform(patch("/api/management/apartments/41/correction")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor_no\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor_no").doesNotExist());

        ArgumentCaptor<ApartmentCorrectionRequest> request = ArgumentCaptor.forClass(ApartmentCorrectionRequest.class);
        verify(apartmentManagementService).correct(org.mockito.ArgumentMatchers.eq(41L),
                request.capture(), any(User.class));
        org.junit.jupiter.api.Assertions.assertTrue(request.getValue().hasFloorNo());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().floorNo());
        org.junit.jupiter.api.Assertions.assertFalse(request.getValue().hasBuilding());
    }

    @Test
    void apartmentDetailAndHistoryReturnOnlyTheirApprovedFields() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime at = LocalDateTime.parse("2026-10-02T09:00:00");
        ApartmentDetail detail = new ApartmentDetail(41L, "Tower A", "A-01", 5, ApartmentStatus.ACTIVE, at, at);
        when(apartmentManagementService.detail(org.mockito.ArgumentMatchers.eq(41L), any(User.class)))
                .thenReturn(detail);
        HistoryItem item = new HistoryItem("APARTMENT_CREATED", at, null, 7L, 41L);
        when(apartmentManagementService.history(41L, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/api/management/apartments/41")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apartment_code").value("A-01"))
                .andExpect(jsonPath("$.floor_no").value(5))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.building_key").doesNotExist());

        mockMvc.perform(get("/api/management/apartments/41/history")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("APARTMENT_CREATED"))
                .andExpect(jsonPath("$.items[0].actor_user_id").value(7))
                .andExpect(jsonPath("$.items[0].subject_id").value(41))
                .andExpect(jsonPath("$.items[0].new_data").doesNotExist());
    }

    @Test
    void apartmentApiMapsBusinessConflictNotFoundAndInvalidRequestWithoutSqlDetails() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE"),
                        new SimpleGrantedAuthority("APARTMENT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(apartmentManagementService.create(any(ApartmentCreateRequest.class), any(User.class)))
                .thenThrow(new ApartmentIdentityConflictException());

        mockMvc.perform(post("/api/management/apartments")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"building\":\"Tower A\",\"apartment_code\":\"A-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APARTMENT_IDENTITY_CONFLICT"))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/management/apartments")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"building\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        when(apartmentManagementService.detail(org.mockito.ArgumentMatchers.eq(77L), any(User.class)))
                .thenThrow(new ApartmentNotFoundException());
        mockMvc.perform(get("/api/management/apartments/77")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("SQLException")));

        when(apartmentManagementService.correct(org.mockito.ArgumentMatchers.eq(77L),
                any(ApartmentCorrectionRequest.class), any(User.class)))
                .thenThrow(new org.springframework.dao.PessimisticLockingFailureException("database lock detail"));
        mockMvc.perform(patch("/api/management/apartments/77/correction")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor_no\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("database lock detail")));
    }

    @Test
    void residentListDetailLookupAndHistoryUseSeparateMinimizedContracts() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(reader, reader, reader, reader);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime at = LocalDateTime.parse("2026-10-02T09:00:00");
        ResidentDetail detail = new ResidentDetail(56L, "Resident Example", "ID-56", null, null, null,
                ResidentStatus.ACTIVE, at, at);
        when(residentManagementService.list("AHR04 Example", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(new ResidentSummary(56L, "Resident Example",
                        ResidentStatus.ACTIVE)), 0, 20, 1));
        when(residentManagementService.detail(org.mockito.ArgumentMatchers.eq(56L), any(User.class)))
                .thenReturn(detail);
        when(residentManagementService.lookupByIdentityNumber(any(ResidentLookupRequest.class), any(User.class)))
                .thenReturn(detail);
        when(residentManagementService.history(56L, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(new HistoryItem("RESIDENT_CREATED", at, null, 7L, 56L)),
                        0, 20, 1));

        mockMvc.perform(get("/api/management/residents")
                        .param("full_name", "AHR04 Example")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].full_name").value("Resident Example"))
                .andExpect(jsonPath("$.items[0].identity_number").doesNotExist());
        mockMvc.perform(get("/api/management/residents/56")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity_number").value("ID-56"));
        mockMvc.perform(post("/api/management/residents/lookup")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity_number\":\"ID-56\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity_number").value("ID-56"));
        mockMvc.perform(get("/api/management/residents/56/history")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("RESIDENT_CREATED"))
                .andExpect(jsonPath("$.items[0].identity_number").doesNotExist());
    }

    @Test
    void residentCreateReturnsCreatedOrReusedStatusAndUsesSnakeCase() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime at = LocalDateTime.parse("2026-10-02T09:00:00");
        ResidentDetail detail = new ResidentDetail(57L, "Create Resident", "ID-57", null, null, null,
                ResidentStatus.ACTIVE, at, at);
        when(residentManagementService.createOrReuse(any(ResidentCreateRequest.class), any(User.class)))
                .thenReturn(new ResidentCreateResult(detail, true), new ResidentCreateResult(detail, false));
        String body = "{\"full_name\":\"Create Resident\",\"identity_number\":\"ID-57\"}";

        mockMvc.perform(post("/api/management/residents")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.full_name").value("Create Resident"))
                .andExpect(jsonPath("$.identity_number").value("ID-57"))
                .andExpect(jsonPath("$.identity_number_key").doesNotExist());
        mockMvc.perform(post("/api/management/residents")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(57));
    }

    @Test
    void residentCorrectionPreservesExplicitNullsAndRequiresManagementPermission() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime at = LocalDateTime.parse("2026-10-02T09:00:00");
        ResidentDetail corrected = new ResidentDetail(58L, "Corrected Resident", "ID-58", null, null, null,
                ResidentStatus.ACTIVE, at, at);
        when(residentManagementService.correct(org.mockito.ArgumentMatchers.eq(58L),
                any(ResidentCorrectionRequest.class), any(User.class))).thenReturn(corrected);

        mockMvc.perform(patch("/api/management/residents/58/correction")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Corrected Resident\",\"identity_number\":\"ID-58\","
                                + "\"date_of_birth\":null,\"phone\":null,\"email\":null,"
                                + "\"reason\":\"Verified identity correction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Corrected Resident"));

        ArgumentCaptor<ResidentCorrectionRequest> request = ArgumentCaptor.forClass(ResidentCorrectionRequest.class);
        verify(residentManagementService).correct(org.mockito.ArgumentMatchers.eq(58L),
                request.capture(), any(User.class));
        org.junit.jupiter.api.Assertions.assertTrue(request.getValue().hasDateOfBirth());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().dateOfBirth());
        org.junit.jupiter.api.Assertions.assertTrue(request.getValue().hasPhone());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().phone());
        org.junit.jupiter.api.Assertions.assertTrue(request.getValue().hasEmail());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().email());
        org.junit.jupiter.api.Assertions.assertEquals("Verified identity correction", request.getValue().reason());
    }

    @Test
    void residentApiMapsNotFoundIdentityConflictAndConcurrentModificationErrors() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_READ"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(residentManagementService.lookupByIdentityNumber(any(ResidentLookupRequest.class), any(User.class)))
                .thenThrow(new ResidentNotFoundException());
        when(residentManagementService.correct(org.mockito.ArgumentMatchers.eq(58L),
                any(ResidentCorrectionRequest.class), any(User.class)))
                .thenThrow(new ResidentIdentityConflictException());
        when(residentManagementService.createOrReuse(any(ResidentCreateRequest.class), any(User.class)))
                .thenThrow(new ResidentConcurrentModificationException());

        mockMvc.perform(post("/api/management/residents/lookup")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity_number\":\"Sensitive-Identity-01\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/management/residents/58/correction")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity_number\":\"Sensitive-Identity-01\",\"reason\":\"Verified\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESIDENT_IDENTITY_CONFLICT"));
        mockMvc.perform(post("/api/management/residents")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Resident\",\"identity_number\":\"Sensitive-Identity-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("Sensitive-Identity-01")));
        mockMvc.perform(post("/api/management/residents")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void residentStatusAllowsAutomaticEffectsButRequiresPermissionsForExplicitNestedActions() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime at = LocalDateTime.parse("2026-10-03T10:00:00");
        ResidentDetail blocked = new ResidentDetail(57L, "Resident", "ID-57", null, null, null,
                ResidentStatus.BLOCKED, at, at);
        when(residentStatusManagementService.changeStatus(org.mockito.ArgumentMatchers.eq(57L),
                any(ResidentStatusChangeRequest.class), any(User.class))).thenReturn(blocked);

        mockMvc.perform(post("/api/management/residents/57/status")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"reason\":\"Administrative block\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        mockMvc.perform(post("/api/management/residents/57/status")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"Inactive\",\"membership_actions\":["
                                + "{\"membership_id\":91,\"action\":\"END\","
                                + "\"effective_at\":\"2026-10-03T10:00:00\",\"reason\":\"Ended\"}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/residents/57/status")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"Inactive\",\"vehicle_right_actions\":["
                                + "{\"relation_id\":92,\"action\":\"REVOKE\","
                                + "\"effective_at\":\"2026-10-03T10:00:00\",\"reason\":\"Revoked\"}]}"))
                .andExpect(status().isForbidden());

        ArgumentCaptor<ResidentStatusChangeRequest> request =
                ArgumentCaptor.forClass(ResidentStatusChangeRequest.class);
        verify(residentStatusManagementService).changeStatus(org.mockito.ArgumentMatchers.eq(57L), request.capture(),
                any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(ResidentStatus.BLOCKED, request.getValue().status());
        org.junit.jupiter.api.Assertions.assertEquals("Administrative block", request.getValue().reason());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().membershipActions());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().vehicleRightActions());
    }

    @Test
    void residentStatusMapsDeactivationGuardToStatusConflict() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(residentStatusManagementService.changeStatus(org.mockito.ArgumentMatchers.eq(57L),
                any(ResidentStatusChangeRequest.class), any(User.class)))
                .thenThrow(new ResidentStatusConflictException());

        mockMvc.perform(post("/api/management/residents/57/status")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"Resident deactivated\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CONFLICT"));
    }

    @Test
    void residentStatusMapsInvalidTransitionToStatusConflict() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("RESIDENT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(residentStatusManagementService.changeStatus(org.mockito.ArgumentMatchers.eq(57L),
                any(ResidentStatusChangeRequest.class), any(User.class)))
                .thenThrow(new ResidentStatusConflictException());

        mockMvc.perform(post("/api/management/residents/57/status")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"reason\":\"Invalid from INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CONFLICT"));
    }

    @Test
    void membershipReadRoutesRequireReadPermissionAndReturnMinimizedContracts() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_READ")));
        when(accountService.loadUserById(7L)).thenReturn(gateStaffAccount(), reader, reader, reader);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime validFrom = LocalDateTime.parse("2027-01-01T00:00:00");
        LocalDateTime createdAt = LocalDateTime.parse("2026-10-03T09:00:00");
        MembershipDetail detail = new MembershipDetail(91L, 41L, 72L, MembershipRole.MEMBER,
                validFrom, null, MembershipStatus.ACTIVE, null, null, createdAt);
        when(apartmentMembershipManagementService.list(41L, 72L, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(detail), 0, 20, 1));
        when(apartmentMembershipManagementService.detail(org.mockito.ArgumentMatchers.eq(91L), any(User.class)))
                .thenReturn(detail);
        when(apartmentMembershipManagementService.history(91L, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(new HistoryItem(
                        "MEMBERSHIP_CREATED", createdAt, "Verified", 7L, 91L)), 0, 20, 1));

        mockMvc.perform(get("/api/management/memberships"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/management/memberships")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/management/memberships")
                        .param("apartment_id", "41")
                        .param("resident_id", "72")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].member_role").value("MEMBER"))
                .andExpect(jsonPath("$.items[0].apartment_id").value(41))
                .andExpect(jsonPath("$.items[0].resident_id").value(72))
                .andExpect(jsonPath("$.items[0].identity_number").doesNotExist())
                .andExpect(jsonPath("$.total_items").value(1));
        mockMvc.perform(get("/api/management/memberships/91")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/management/memberships/91/history")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("MEMBERSHIP_CREATED"))
                .andExpect(jsonPath("$.items[0].reason").value("Verified"));

        verify(apartmentMembershipManagementService).list(41L, 72L, 0, 20);
    }

    @Test
    void membershipCommandsRequireManagePermissionAndUseApprovedRoutes() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_READ")));
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(reader, manager, manager, manager, manager, reader, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime start = LocalDateTime.parse("2027-01-01T00:00:00");
        LocalDateTime createdAt = LocalDateTime.parse("2026-10-03T09:00:00");
        MembershipDetail member = new MembershipDetail(91L, 41L, 72L, MembershipRole.MEMBER,
                start, null, MembershipStatus.ACTIVE, null, null, createdAt);
        MembershipDetail head = new MembershipDetail(92L, 41L, 73L, MembershipRole.HOUSEHOLD_HEAD,
                start, null, MembershipStatus.ACTIVE, null, null, createdAt);
        MembershipDetail ended = new MembershipDetail(91L, 41L, 72L, MembershipRole.MEMBER,
                start, start.plusDays(10), MembershipStatus.INACTIVE, createdAt, "Ended", createdAt);
        MembershipDetail revoked = new MembershipDetail(92L, 41L, 73L, MembershipRole.HOUSEHOLD_HEAD,
                start, start.plusDays(10), MembershipStatus.REVOKED, createdAt, "Revoked", createdAt);
        MembershipDetail voided = new MembershipDetail(93L, 41L, 75L, MembershipRole.MEMBER,
                start, null, MembershipStatus.VOID, createdAt, "Created in error", createdAt);
        when(apartmentMembershipManagementService.add(any(MembershipCreateRequest.class), any(User.class)))
                .thenReturn(member);
        when(apartmentMembershipManagementService.assignHouseholdHead(org.mockito.ArgumentMatchers.eq(41L),
                any(MembershipHeadAssignRequest.class), any(User.class))).thenReturn(head);
        when(apartmentMembershipManagementService.end(org.mockito.ArgumentMatchers.eq(91L),
                any(MembershipLifecycleRequest.class), any(User.class))).thenReturn(ended);
        when(apartmentMembershipManagementService.revoke(org.mockito.ArgumentMatchers.eq(92L),
                any(MembershipLifecycleRequest.class), any(User.class))).thenReturn(revoked);
        when(apartmentMembershipManagementService.voidMembership(org.mockito.ArgumentMatchers.eq(93L),
                any(MembershipVoidRequest.class), any(User.class))).thenReturn(voided);

        String memberBody = "{\"apartment_id\":41,\"resident_id\":72,\"member_role\":\"MEMBER\","
                + "\"valid_from\":\"2027-01-01T00:00:00\",\"valid_to\":null,\"reason\":\"Verified\"}";
        mockMvc.perform(post("/api/management/memberships")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(memberBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/memberships")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(memberBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member_role").value("MEMBER"));
        mockMvc.perform(post("/api/management/apartments/41/household-head/assign")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resident_id\":73,\"valid_from\":\"2027-01-01T00:00:00\","
                                + "\"valid_to\":null,\"reason\":\"Verified head\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member_role").value("HOUSEHOLD_HEAD"));
        mockMvc.perform(post("/api/management/memberships/91/end")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2027-01-11T00:00:00\",\"reason\":\"Ended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/management/memberships/92/revoke")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2027-01-11T00:00:00\",\"reason\":\"Revoked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        mockMvc.perform(post("/api/management/memberships/93/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Created in error\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/memberships/93/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Created in error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.valid_from").value("2027-01-01T00:00:00"));
    }

    @Test
    void membershipApiMapsConflictsAndNotFoundWithoutInternalDetails() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_MANAGE"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_READ")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(apartmentMembershipManagementService.add(any(MembershipCreateRequest.class), any(User.class)))
                .thenThrow(new MembershipOverlapException());
        when(apartmentMembershipManagementService.assignHouseholdHead(org.mockito.ArgumentMatchers.eq(41L),
                any(MembershipHeadAssignRequest.class), any(User.class)))
                .thenThrow(new HouseholdHeadConflictException());
        when(apartmentMembershipManagementService.end(org.mockito.ArgumentMatchers.eq(91L),
                any(MembershipLifecycleRequest.class), any(User.class)))
                .thenThrow(new MembershipStateConflictException());
        when(apartmentMembershipManagementService.revoke(org.mockito.ArgumentMatchers.eq(92L),
                any(MembershipLifecycleRequest.class), any(User.class)))
                .thenThrow(new MembershipStatusConflictException());
        when(apartmentMembershipManagementService.voidMembership(org.mockito.ArgumentMatchers.eq(93L),
                any(MembershipVoidRequest.class), any(User.class)))
                .thenThrow(new MembershipStateConflictException());
        when(apartmentMembershipManagementService.voidMembership(org.mockito.ArgumentMatchers.eq(404L),
                any(MembershipVoidRequest.class), any(User.class)))
                .thenThrow(new MembershipNotFoundException());
        when(apartmentMembershipManagementService.detail(org.mockito.ArgumentMatchers.eq(404L), any(User.class)))
                .thenThrow(new MembershipNotFoundException());
        String body = "{\"apartment_id\":41,\"resident_id\":72,\"member_role\":\"MEMBER\","
                + "\"valid_from\":\"2027-01-01T00:00:00\",\"reason\":\"Verified\"}";

        mockMvc.perform(post("/api/management/memberships")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_OVERLAP"));
        mockMvc.perform(post("/api/management/apartments/41/household-head/assign")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resident_id\":73,\"valid_from\":\"2027-01-01T00:00:00\","
                                + "\"reason\":\"Verified head\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_HEAD_CONFLICT"));
        mockMvc.perform(post("/api/management/memberships/91/end")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2027-01-11T00:00:00\",\"reason\":\"Ended\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELATION_STATE_CONFLICT"));
        mockMvc.perform(post("/api/management/memberships/92/revoke")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2027-01-11T00:00:00\",\"reason\":\"Revoked\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CONFLICT"));
        mockMvc.perform(post("/api/management/memberships/93/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Invalid VOID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELATION_STATE_CONFLICT"));
        mockMvc.perform(post("/api/management/memberships/404/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unknown Membership\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/management/memberships/93/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/management/memberships/93/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/management/memberships")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apartment_id\":41,\"resident_id\":72,\"member_role\":\"MEMBER\","
                                + "\"valid_from\":\"2027-01-01T00:00:00\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/management/memberships/404")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void householdHeadTransferUsesOnlyItsSourcePermissionForAutomaticVehicleEffects() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_READ")));
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_MANAGE")));
        org.junit.jupiter.api.Assertions.assertFalse(manager.authorities().stream()
                .anyMatch(authority -> "VEHICLE_RIGHT_MANAGE".equals(authority.getAuthority())));
        when(accountService.loadUserById(7L)).thenReturn(reader, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime effectiveAt = LocalDateTime.parse("2027-01-01T00:00:00");
        MembershipDetail transferred = new MembershipDetail(93L, 41L, 74L, MembershipRole.HOUSEHOLD_HEAD,
                effectiveAt, null, MembershipStatus.ACTIVE, null, null,
                LocalDateTime.parse("2026-10-03T09:00:00"));
        when(apartmentMembershipManagementService.transferHouseholdHead(org.mockito.ArgumentMatchers.eq(41L),
                any(MembershipTransferRequest.class), any(User.class))).thenReturn(transferred);
        String body = "{\"from_membership_id\":91,\"to_resident_id\":74,"
                + "\"effective_at\":\"2027-01-01T00:00:00\",\"reason\":\"Household head changed\"}";

        mockMvc.perform(post("/api/management/apartments/41/household-head/transfer")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/apartments/41/household-head/transfer")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(93))
                .andExpect(jsonPath("$.member_role").value("HOUSEHOLD_HEAD"));

        ArgumentCaptor<MembershipTransferRequest> request = ArgumentCaptor.forClass(MembershipTransferRequest.class);
        verify(apartmentMembershipManagementService).transferHouseholdHead(
                org.mockito.ArgumentMatchers.eq(41L), request.capture(), any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(91L, request.getValue().fromMembershipId());
        org.junit.jupiter.api.Assertions.assertEquals(74L, request.getValue().toResidentId());
        org.junit.jupiter.api.Assertions.assertEquals(effectiveAt, request.getValue().effectiveAt());
        org.junit.jupiter.api.Assertions.assertEquals("Household head changed", request.getValue().reason());
    }

    @Test
    void apartmentDeactivationRequiresMembershipPermissionOnlyForNestedEnds() throws Exception {
        AuthenticatedAccount apartmentManager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE")));
        AuthenticatedAccount membershipManager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(apartmentManager, apartmentManager,
                membershipManager, membershipManager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime createdAt = LocalDateTime.parse("2026-10-03T09:00:00");
        ApartmentDetail inactive = new ApartmentDetail(41L, "Tower A", "A-01", 5,
                ApartmentStatus.INACTIVE, createdAt, createdAt.plusMinutes(1));
        ApartmentDetail active = new ApartmentDetail(41L, "Tower A", "A-01", 5,
                ApartmentStatus.ACTIVE, createdAt, createdAt.plusMinutes(2));
        when(apartmentStatusManagementService.deactivate(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentDeactivationRequest.class), any(User.class))).thenReturn(inactive, inactive);
        when(apartmentStatusManagementService.reactivate(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentReactivationRequest.class), any(User.class))).thenReturn(active);

        mockMvc.perform(post("/api/management/apartments/41/deactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment closed\",\"membership_ends\":["
                                + "{\"membership_id\":91,\"effective_at\":\"2026-10-02T00:00:00\","
                                + "\"reason\":\"Membership ended\"}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/apartments/41/deactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/management/apartments/41/deactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment closed\",\"membership_ends\":["
                                + "{\"membership_id\":91,\"effective_at\":\"2026-10-02T00:00:00\","
                                + "\"reason\":\"Membership ended\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/management/apartments/41/reactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment reopened\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(apartmentStatusManagementService,
                org.mockito.Mockito.times(2)).deactivate(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentDeactivationRequest.class), any(User.class));
        verify(apartmentStatusManagementService).reactivate(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentReactivationRequest.class), any(User.class));
    }

    @Test
    void vehicleLookupRequiresReadPermissionAndReturnsMinimizedSummary() throws Exception {
        AuthenticatedAccount managementWithoutVehicleRead = new AuthenticatedAccount(7L, "manager",
                UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT")));
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(
                gateStaffAccount(), managementWithoutVehicleRead, reader, reader);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        VehicleSummary vehicle = new VehicleSummary(44L, "51A-123.45", 17L, "Toyota", VehicleStatus.ACTIVE);
        when(vehicleManagementService.list("NORMALIZED-51A12345", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(vehicle), 0, 20, 1));
        when(vehicleManagementService.detail(org.mockito.ArgumentMatchers.eq(44L), any(User.class)))
                .thenReturn(vehicle);

        mockMvc.perform(get("/api/management/vehicles"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/management/vehicles")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/management/vehicles")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/management/vehicles")
                        .param("plate_number", "NORMALIZED-51A12345")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(44))
                .andExpect(jsonPath("$.items[0].plate_number").value("51A-123.45"))
                .andExpect(jsonPath("$.items[0].vehicle_category_id").value(17))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].plate_normalized").doesNotExist())
                .andExpect(jsonPath("$.items[0].model").doesNotExist())
                .andExpect(jsonPath("$.total_items").value(1));
        mockMvc.perform(get("/api/management/vehicles/44")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Toyota"));

        verify(vehicleManagementService).list("NORMALIZED-51A12345", 0, 20);
        verify(vehicleManagementService).detail(org.mockito.ArgumentMatchers.eq(44L), any(User.class));
    }

    @Test
    void ownerAssignmentAndTransferRequireManagePermissionAndUseApprovedContracts() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ")));
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(reader, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime validFrom = LocalDateTime.parse("2027-01-01T00:00:00");
        LocalDateTime effectiveAt = LocalDateTime.parse("2027-02-01T00:00:00");
        VehicleRightDetail owner = new VehicleRightDetail(81L, 44L, 72L, VehicleRelationType.OWNER,
                null, null, null, validFrom, null, VehicleRelationStatus.ACTIVE, null, null, validFrom);
        VehicleRightDetail transferred = new VehicleRightDetail(82L, 44L, 73L, VehicleRelationType.OWNER,
                null, null, null, effectiveAt, null, VehicleRelationStatus.ACTIVE, null, null, validFrom);
        when(vehicleManagementService.assignOwner(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleOwnerAssignmentRequest.class), any(User.class))).thenReturn(owner);
        when(vehicleManagementService.transferOwner(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleOwnerTransferRequest.class), any(User.class))).thenReturn(transferred);
        String assignmentBody = "{\"resident_id\":72,\"valid_from\":\"2027-01-01T00:00:00\","
                + "\"reason\":\"Owner verified\"}";
        String transferBody = "{\"from_relation_id\":81,\"to_resident_id\":73,"
                + "\"effective_at\":\"2027-02-01T00:00:00\",\"reason\":\"Owner changed\"}";

        mockMvc.perform(post("/api/management/vehicles/44/owner")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(assignmentBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/vehicles/44/owner")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(assignmentBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relation_type").value("OWNER"))
                .andExpect(jsonPath("$.guarantor_type").doesNotExist());
        mockMvc.perform(post("/api/management/vehicles/44/owner/transfer")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(82))
                .andExpect(jsonPath("$.resident_id").value(73))
                .andExpect(jsonPath("$.valid_from").value("2027-02-01T00:00:00"));

        ArgumentCaptor<VehicleOwnerAssignmentRequest> assignment =
                ArgumentCaptor.forClass(VehicleOwnerAssignmentRequest.class);
        verify(vehicleManagementService).assignOwner(org.mockito.ArgumentMatchers.eq(44L), assignment.capture(),
                any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(72L, assignment.getValue().residentId());
        org.junit.jupiter.api.Assertions.assertEquals(validFrom, assignment.getValue().validFrom());
        org.junit.jupiter.api.Assertions.assertEquals("Owner verified", assignment.getValue().reason());
        ArgumentCaptor<VehicleOwnerTransferRequest> transfer = ArgumentCaptor.forClass(VehicleOwnerTransferRequest.class);
        verify(vehicleManagementService).transferOwner(org.mockito.ArgumentMatchers.eq(44L), transfer.capture(),
                any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(81L, transfer.getValue().fromRelationId());
        org.junit.jupiter.api.Assertions.assertEquals(73L, transfer.getValue().toResidentId());
        org.junit.jupiter.api.Assertions.assertEquals(effectiveAt, transfer.getValue().effectiveAt());
    }

    @Test
    void authorizedUserQueriesRequireReadPermissionAndReturnOnlyVehicleRelationFields() throws Exception {
        AuthenticatedAccount managementWithoutRead = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE,
                null, List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT")));
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(gateStaffAccount(), managementWithoutRead, reader, reader);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime validFrom = LocalDateTime.parse("2027-01-01T00:00:00");
        VehicleRightDetail grant = new VehicleRightDetail(91L, 44L, 72L, VehicleRelationType.AUTHORIZED_USER,
                VehicleRelationGuarantorType.OWNER, 73L, null, validFrom, null,
                VehicleRelationStatus.ACTIVE, null, null, validFrom);
        when(authorizedUserGrantService.list(44L, 72L, VehicleRelationType.AUTHORIZED_USER, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(grant), 0, 20, 1));
        when(authorizedUserGrantService.detail(org.mockito.ArgumentMatchers.eq(91L), any(User.class)))
                .thenReturn(grant);
        when(authorizedUserGrantService.history(91L, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(new HistoryItem(
                        "VEHICLE_AUTHORIZED_USER_GRANTED", validFrom, "Approved", 7L, 91L)), 0, 20, 1));

        mockMvc.perform(get("/api/management/vehicle-rights"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/management/vehicle-rights")
                        .param("vehicle_id", "44").param("resident_id", "72")
                        .param("relation_type", "AUTHORIZED_USER")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].relation_type").value("AUTHORIZED_USER"))
                .andExpect(jsonPath("$.items[0].guarantor_type").value("OWNER"))
                .andExpect(jsonPath("$.items[0].resident_id").value(72))
                .andExpect(jsonPath("$.items[0].identity_number").doesNotExist())
                .andExpect(jsonPath("$.total_items").value(1));
        mockMvc.perform(get("/api/management/vehicle-rights/91")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guarantor_resident_id").value(73));
        mockMvc.perform(get("/api/management/vehicle-rights/91/history")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("Approved"));

        verify(authorizedUserGrantService).list(44L, 72L, VehicleRelationType.AUTHORIZED_USER, 0, 20);
        verify(authorizedUserGrantService).detail(org.mockito.ArgumentMatchers.eq(91L), any(User.class));
        verify(authorizedUserGrantService).history(91L, 0, 20);
    }

    @Test
    void authorizedUserGrantRequiresManagePermissionAndUsesApprovedJsonContract() throws Exception {
        AuthenticatedAccount reader = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ")));
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(gateStaffAccount(), reader, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime validFrom = LocalDateTime.parse("2027-01-01T00:00:00");
        VehicleRightDetail created = new VehicleRightDetail(91L, 44L, 72L,
                VehicleRelationType.AUTHORIZED_USER, VehicleRelationGuarantorType.HOUSEHOLD_HEAD,
                73L, 12L, validFrom, null, VehicleRelationStatus.ACTIVE, null, null, validFrom);
        when(authorizedUserGrantService.grantAuthorizedUser(org.mockito.ArgumentMatchers.eq(44L),
                any(AuthorizedUserGrantRequest.class), any(User.class))).thenReturn(created);
        String body = "{\"vehicle_id\":44,\"resident_id\":72,\"guarantor_type\":\"HOUSEHOLD_HEAD\","
                + "\"guarantor_resident_id\":73,\"guarantor_apartment_id\":12,"
                + "\"valid_from\":\"2027-01-01T00:00:00\",\"reason\":\"Household head approved\"}";

        mockMvc.perform(post("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relation_type").value("AUTHORIZED_USER"))
                .andExpect(jsonPath("$.guarantor_type").value("HOUSEHOLD_HEAD"))
                .andExpect(jsonPath("$.guarantor_apartment_id").value(12));

        ArgumentCaptor<AuthorizedUserGrantRequest> request = ArgumentCaptor.forClass(AuthorizedUserGrantRequest.class);
        verify(authorizedUserGrantService).grantAuthorizedUser(org.mockito.ArgumentMatchers.eq(44L),
                request.capture(), any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(72L, request.getValue().residentId());
        org.junit.jupiter.api.Assertions.assertEquals(73L, request.getValue().guarantorResidentId());
        org.junit.jupiter.api.Assertions.assertEquals(12L, request.getValue().guarantorApartmentId());
        org.junit.jupiter.api.Assertions.assertEquals(validFrom, request.getValue().validFrom());
        org.junit.jupiter.api.Assertions.assertEquals("Household head approved", request.getValue().reason());
    }

    @Test
    void authorizedUserGrantMapsGuarantorAndStatusConflictsWithoutInternalDetails() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(authorizedUserGrantService.grantAuthorizedUser(org.mockito.ArgumentMatchers.eq(44L),
                any(AuthorizedUserGrantRequest.class), any(User.class)))
                .thenThrow(new GuarantorChainConflictException(), new VehicleStatusConflictException());
        String body = "{\"vehicle_id\":44,\"resident_id\":72,\"guarantor_type\":\"OWNER\","
                + "\"guarantor_resident_id\":73,\"valid_from\":\"2027-01-01T00:00:00\","
                + "\"reason\":\"Owner approved\"}";

        mockMvc.perform(post("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUARANTOR_CHAIN_CONFLICT"));
        mockMvc.perform(post("/api/management/vehicle-rights")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("SQL"))));
    }

    @Test
    void vehicleRightLifecycleRoutesUseTheApprovedSnakeCaseContractAndManagePermission() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        LocalDateTime validFrom = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime effectiveAt = LocalDateTime.parse("2026-10-02T00:00:00");
        VehicleRightDetail ended = new VehicleRightDetail(44L, 31L, 72L, VehicleRelationType.AUTHORIZED_USER,
                VehicleRelationGuarantorType.OWNER, 73L, null, validFrom, effectiveAt,
                VehicleRelationStatus.INACTIVE, effectiveAt.plusSeconds(1), "Ended", effectiveAt.minusDays(2));
        VehicleRightDetail revoked = new VehicleRightDetail(45L, 31L, 74L, VehicleRelationType.AUTHORIZED_USER,
                VehicleRelationGuarantorType.OWNER, 73L, null, validFrom, effectiveAt,
                VehicleRelationStatus.REVOKED, effectiveAt.plusSeconds(1), "Revoked", effectiveAt.minusDays(2));
        VehicleRightDetail voided = new VehicleRightDetail(46L, 31L, 75L, VehicleRelationType.AUTHORIZED_USER,
                VehicleRelationGuarantorType.OWNER, 73L, null, validFrom, null,
                VehicleRelationStatus.VOID, effectiveAt, "Created in error", effectiveAt.minusDays(2));
        when(vehicleRightLifecycleService.end(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightLifecycleRequest.class), any(User.class))).thenReturn(ended);
        when(vehicleRightLifecycleService.revoke(org.mockito.ArgumentMatchers.eq(45L),
                any(VehicleRightLifecycleRequest.class), any(User.class))).thenReturn(revoked);
        when(vehicleRightLifecycleService.voidRight(org.mockito.ArgumentMatchers.eq(46L),
                any(VehicleRightVoidRequest.class), any(User.class))).thenReturn(voided);

        mockMvc.perform(post("/api/management/vehicle-rights/44/end")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2026-10-02T00:00:00\",\"reason\":\"Ended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relation_type").value("AUTHORIZED_USER"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.valid_to").value("2026-10-02T00:00:00"))
                .andExpect(jsonPath("$.lifecycle_reason").value("Ended"));
        mockMvc.perform(post("/api/management/vehicle-rights/45/revoke")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2026-10-02T00:00:00\",\"reason\":\"Revoked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.lifecycle_reason").value("Revoked"));
        mockMvc.perform(post("/api/management/vehicle-rights/46/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Created in error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.valid_to").doesNotExist())
                .andExpect(jsonPath("$.lifecycle_reason").value("Created in error"));

        ArgumentCaptor<VehicleRightLifecycleRequest> request =
                ArgumentCaptor.forClass(VehicleRightLifecycleRequest.class);
        verify(vehicleRightLifecycleService).end(org.mockito.ArgumentMatchers.eq(44L), request.capture(),
                any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals(effectiveAt, request.getValue().effectiveAt());
        org.junit.jupiter.api.Assertions.assertEquals("Ended", request.getValue().reason());
        org.mockito.ArgumentCaptor<VehicleRightVoidRequest> voidRequest =
                org.mockito.ArgumentCaptor.forClass(VehicleRightVoidRequest.class);
        verify(vehicleRightLifecycleService).voidRight(org.mockito.ArgumentMatchers.eq(46L), voidRequest.capture(),
                any(User.class));
        org.junit.jupiter.api.Assertions.assertEquals("Created in error", voidRequest.getValue().reason());
    }

    @Test
    void vehicleRightLifecycleRequiresManagePermissionAndMapsScopedErrors() throws Exception {
        AuthenticatedAccount readOnly = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ")));
        when(accountService.loadUserById(7L)).thenReturn(readOnly, readOnly);
        mockMvc.perform(post("/api/management/vehicle-rights/44/end")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2026-10-02T00:00:00\",\"reason\":\"Ended\"}"))
                .andExpect(status().isForbidden());
        verify(vehicleRightLifecycleService, never()).end(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightLifecycleRequest.class), any(User.class));
        mockMvc.perform(post("/api/management/vehicle-rights/44/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Created in error\"}"))
                .andExpect(status().isForbidden());
        verify(vehicleRightLifecycleService, never()).voidRight(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightVoidRequest.class), any(User.class));

        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(vehicleRightLifecycleService.end(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightLifecycleRequest.class), any(User.class)))
                .thenThrow(new VehicleRelationStateConflictException());
        when(vehicleRightLifecycleService.revoke(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightLifecycleRequest.class), any(User.class)))
                .thenThrow(new VehicleNotFoundException());
        when(vehicleRightLifecycleService.voidRight(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleRightVoidRequest.class), any(User.class)))
                .thenThrow(new VehicleRelationStateConflictException());
        when(vehicleRightLifecycleService.voidRight(org.mockito.ArgumentMatchers.eq(45L),
                any(VehicleRightVoidRequest.class), any(User.class)))
                .thenThrow(new VehicleNotFoundException());

        mockMvc.perform(post("/api/management/vehicle-rights/44/end")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2026-10-02T00:00:00\",\"reason\":\"Ended\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELATION_STATE_CONFLICT"));
        mockMvc.perform(post("/api/management/vehicle-rights/44/revoke")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effective_at\":\"2026-10-02T00:00:00\",\"reason\":\"Revoked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/management/vehicle-rights/44/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Invalid VOID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELATION_STATE_CONFLICT"));
        mockMvc.perform(post("/api/management/vehicle-rights/45/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Missing right\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/management/vehicle-rights/44/void")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void vehicleOwnerApiMapsBusinessConflictsNotFoundAndInvalidRequests() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_READ"),
                        new SimpleGrantedAuthority("VEHICLE_RIGHT_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager, manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(vehicleManagementService.assignOwner(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleOwnerAssignmentRequest.class), any(User.class)))
                .thenThrow(new VehicleOwnerConflictException());
        when(vehicleManagementService.transferOwner(org.mockito.ArgumentMatchers.eq(44L),
                any(VehicleOwnerTransferRequest.class), any(User.class)))
                .thenThrow(new VehicleRightOverlapException());
        when(vehicleManagementService.detail(org.mockito.ArgumentMatchers.eq(404L), any(User.class)))
                .thenThrow(new VehicleNotFoundException());

        mockMvc.perform(post("/api/management/vehicles/44/owner")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resident_id\":72,\"valid_from\":\"2027-01-01T00:00:00\","
                                + "\"reason\":\"Owner verified\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_OWNER_CONFLICT"));
        mockMvc.perform(post("/api/management/vehicles/44/owner/transfer")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from_relation_id\":81,\"to_resident_id\":73,"
                                + "\"effective_at\":\"2027-02-01T00:00:00\",\"reason\":\"Owner changed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_RIGHT_OVERLAP"));
        mockMvc.perform(post("/api/management/vehicles/44/owner")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resident_id\":72,\"valid_from\":\"2027-01-01T00:00:00\","
                                + "\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/management/vehicles/404")
                        .header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void apartmentLifecycleConflictsUseTheScopedErrorEnvelope() throws Exception {
        AuthenticatedAccount manager = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("APARTMENT_MANAGE"),
                        new SimpleGrantedAuthority("HOUSEHOLD_MEMBERSHIP_MANAGE")));
        when(accountService.loadUserById(7L)).thenReturn(manager, manager);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(apartmentStatusManagementService.deactivate(org.mockito.ArgumentMatchers.eq(41L),
                any(ApartmentDeactivationRequest.class), any(User.class)))
                .thenThrow(new ApartmentStatusConflictException(), new MembershipStateConflictException());

        mockMvc.perform(post("/api/management/apartments/41/deactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment closed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CONFLICT"));
        mockMvc.perform(post("/api/management/apartments/41/deactivate")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Apartment closed\",\"membership_ends\":["
                                + "{\"membership_id\":91,\"effective_at\":\"2026-10-02T00:00:00\","
                                + "\"reason\":\"Membership ended\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELATION_STATE_CONFLICT"));
    }

    @Test
    void webStateChangingRequestRequiresCsrf() throws Exception {
        mockMvc.perform(post("/web/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void generatedAssetsAreAnonymousWhileApplicationRoutesRemainProtected() throws Exception {
        mockMvc.perform(get("/assets/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")));

        mockMvc.perform(get("/assets/login.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/assets/account-security.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/assets/chunks/lucide.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/web/auth/change-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicAndAuthenticatedWebPagesSendSelfHostedContentSecurityPolicy() throws Exception {
        String policy = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
                + "form-action 'self'; frame-src 'none'; frame-ancestors 'none'";

        mockMvc.perform(get("/login"))
                .andExpect(result -> assertWebContentSecurityPolicy(result, policy));

        mockMvc.perform(get("/").with(user("manager").roles("MANAGEMENT")))
                .andExpect(result -> assertWebContentSecurityPolicy(result, policy));

        mockMvc.perform(get("/account/security").with(user("manager").authorities(
                        new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD"))))
                .andExpect(result -> assertWebContentSecurityPolicy(result, policy));
    }

    private void assertWebContentSecurityPolicy(MvcResult result, String expectedPolicy) {
        org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                expectedPolicy, result.getResponse().getHeader("Content-Security-Policy"));
    }

    @Test
    void anonymousManagementHomeRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void anonymousLoginPageRendersCredentialSemanticsAndCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().size(0))
                .andReturn();
        String html = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"username\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("for=\"username\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("autocomplete=\"username\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("for=\"password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("type=\"password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("autocomplete=\"current-password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("aria-describedby=\"login-feedback\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("aria-live=\"polite\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("data-password-toggle"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("method=\"post\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/login\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("/assets/login.js"));
        assertTrue(Pattern.compile("name=\"_csrf\"\\s+value=\"[^\"]+\"").matcher(html).find());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/account/security"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("data-logout-form"));
    }

    @Test
    void authenticatedManagementIsRedirectedFromLoginToHome() throws Exception {
        mockMvc.perform(get("/login").with(user("manager").roles("MANAGEMENT")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
    }

    @Test
    void managementHomeShowsOnlyAuthenticatedIdentityAndRoleContext() throws Exception {
        MvcResult result = mockMvc.perform(get("/").with(user("manager").roles("MANAGEMENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("app/home"))
                .andExpect(content().string(containsString(
                        "id=\"management-home-title\">Khu vực Ban quản lý</h1>")))
                .andReturn();

        java.util.Map<String, Object> model = result.getModelAndView().getModel();
        org.junit.jupiter.api.Assertions.assertEquals("manager", model.get("username"));
        org.junit.jupiter.api.Assertions.assertEquals("MANAGEMENT", model.get("role"));
        org.junit.jupiter.api.Assertions.assertEquals(2, model.size());
        String html = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("manager"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("MANAGEMENT"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("WORKSPACE / WEB"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Smart Parking"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("href=\"/account/security\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/logout\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("data-logout-form"));
        assertTrue(Pattern.compile("name=\"_csrf\"\\s+value=\"[^\"]+\"").matcher(html).find());
    }

    @Test
    void managementCanRenderAccountSecurityFormWithCsrfAndPasswordManagerSemantics() throws Exception {
        MvcResult result = mockMvc.perform(get("/account/security").with(user("manager").authorities(
                        new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD"))))
                .andExpect(status().isOk())
                .andExpect(view().name("account/security"))
                .andExpect(content().string(containsString("id=\"account-security-title\"")))
                .andReturn();
        String html = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"current_password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("autocomplete=\"current-password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"new_password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"confirmation\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("autocomplete=\"new-password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/web/auth/change-password\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("href=\"/\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("/assets/account-security.js"));
        assertTrue(Pattern.compile("name=\"_csrf\"\\s+value=\"[^\"]+\"").matcher(html).find());
    }

    @Test
    void accountSecurityPageRequiresManagementAndPasswordChangeAuthority() throws Exception {
        mockMvc.perform(get("/account/security"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/account/security").with(user("staff").authorities(
                        new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/account/security").with(user("manager").roles("MANAGEMENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gateStaffCannotRenderTheManagementHome() throws Exception {
        mockMvc.perform(get("/").with(user("gate.staff").roles("GATE_STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(head("/").with(user("gate.staff").roles("GATE_STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void webSessionUsesThirtyMinuteIdleTimeout() {
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofMinutes(30), configuredIdleTimeout);
    }

    @Test
    void requestDetailsDistinguishWebAndRestAuthenticationChannels() {
        AuthRequestDetailsSource detailsSource = new AuthRequestDetailsSource();
        MockHttpServletRequest webRequest = new MockHttpServletRequest();
        webRequest.setRequestURI("/login");
        webRequest.setServletPath("/login");
        MockHttpServletRequest restRequest = new MockHttpServletRequest();
        restRequest.setRequestURI("/api/auth/login");
        restRequest.setServletPath("/api/auth/login");

        org.junit.jupiter.api.Assertions.assertEquals(
                AuthRequestDetails.Channel.WEB, detailsSource.buildDetails(webRequest).channel());
        org.junit.jupiter.api.Assertions.assertEquals(
                AuthRequestDetails.Channel.REST, detailsSource.buildDetails(restRequest).channel());
    }

    @Test
    void webLoginCreatesSessionAndPasswordChangeInvalidatesIt() throws Exception {
        AuthenticatedAccount account = account();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(account, null, account.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(account);
        MockHttpSession preAuthenticationSession = new MockHttpSession();
        String preAuthenticationSessionId = preAuthenticationSession.getId();

        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .session(preAuthenticationSession)
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isNoContent())
                .andReturn();
        ArgumentCaptor<Authentication> loginRequest = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(loginRequest.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuthRequestDetails.Channel.WEB,
                ((AuthRequestDetails) loginRequest.getValue().getDetails()).channel());
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotEquals(preAuthenticationSessionId, session.getId());

        mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/web/auth/change-password").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webSessionFromBeforeCredentialChangeIsRejectedOnNextRequest() throws Exception {
        java.time.LocalDateTime before = java.time.LocalDateTime.parse("2026-09-27T10:00:00.123456");
        AuthenticatedAccount prior = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, before,
                account().authorities());
        AuthenticatedAccount current = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE,
                before.plusNanos(1_000), prior.authorities());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(prior, null, prior.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(current);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "manager")
                        .param("password", "current-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession priorSession = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/account/security").session(priorSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webPasswordChangeReturnsGenericUnauthorizedForWrongCurrentPassword() throws Exception {
        AuthenticatedAccount account = account();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(account, null, account.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(account);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "manager")
                        .param("password", "current-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        doThrow(new org.springframework.security.authentication.BadCredentialsException("Authentication failed"))
                .when(passwordChangeService).changePassword(7L, "wrong-current", "replacement-password");

        MvcResult result = mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"wrong-current\",\"new_password\":\"replacement-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("wrong-current"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("replacement-password"));
    }

    @Test
    void webPasswordChangeReturnsBadRequestForPolicyViolations() throws Exception {
        AuthenticatedAccount account = account();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(account, null, account.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(account);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "manager")
                        .param("password", "current-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        doThrow(new PasswordPolicyViolationException("Password does not meet policy"))
                .when(passwordChangeService).changePassword(7L, "current-password", "short");

        mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"current-password\",\"new_password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    void failedWebLoginDoesNotEstablishAnAuthenticatedSession() throws Exception {
        doThrow(new org.springframework.security.authentication.BadCredentialsException("Authentication failed"))
                .when(authenticationManager).authenticate(any(Authentication.class));
        MockHttpSession preAuthenticationSession = new MockHttpSession();
        String preAuthenticationSessionId = preAuthenticationSession.getId();

        MvcResult failure = mockMvc.perform(post("/login").with(csrf())
                        .session(preAuthenticationSession)
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MockHttpSession session = (MockHttpSession) failure.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertEquals(preAuthenticationSessionId, session.getId());
        org.junit.jupiter.api.Assertions.assertNull(
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void throttledWebLoginReturnsTooManyRequests() throws Exception {
        doThrow(new LoginThrottledException()).when(authenticationManager).authenticate(any(Authentication.class));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void webLogoutInvalidatesTheAuthenticatedSession() throws Exception {
        AuthenticatedAccount account = account();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(account, null, account.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(account);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/web/auth/change-password").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webSessionExceedingEightHourAbsoluteLifetimeIsRejected() throws Exception {
        AuthenticatedAccount account = account();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(account, null, account.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(account);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        session.setAttribute(WebAuthenticationSuccessHandler.AUTHENTICATED_AT,
                System.currentTimeMillis() - java.time.Duration.ofHours(9).toMillis());

        mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRestRequestWithoutBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedBearerUsesCurrentPermissionAndLogoutDoesNotRevokeCopiedToken() throws Exception {
        AuthenticatedAccount permitted = account();
        AuthenticatedAccount noPermission = new AuthenticatedAccount(7L, "gate.staff", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF")));
        when(accountService.loadUserById(7L)).thenReturn(permitted, permitted, noPermission);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.empty());
        String bearer = "Bearer " + issueJwt(7L, "");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordPolicyViolationReturnsBadRequest() throws Exception {
        when(accountService.loadUserById(7L)).thenReturn(account());
        doThrow(new PasswordPolicyViolationException("Password does not meet policy"))
                .when(passwordChangeService).changePassword(7L, "old-password", "new-password-1");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + issueJwt(7L, ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    void validBearerForDisabledAccountReturnsUnauthorized() throws Exception {
        when(accountService.loadUserById(7L)).thenReturn(
                new AuthenticatedAccount(7L, "gate.staff", UserStatus.DISABLED, null, List.of()));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + issueJwt(7L, "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeWebSessionUsesUpdatedPermissionsOnEachRequest() throws Exception {
        AuthenticatedAccount loginAccount = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
        AuthenticatedAccount currentAccount = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(loginAccount, null, loginAccount.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(currentAccount);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "manager")
                        .param("password", "valid-password-1"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managementWebSessionsAreInvalidatedAfterRoleReassignmentToGateStaff() throws Exception {
        AuthenticatedAccount management = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null, List.of(
                new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
        AuthenticatedAccount reassignedGateStaff = new AuthenticatedAccount(7L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(management, null, management.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(reassignedGateStaff);

        MockHttpSession passwordChangeSession = authenticatedManagementSession();
        mockMvc.perform(post("/web/auth/change-password").session(passwordChangeSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"current-password\","
                                + "\"new_password\":\"replacement-password\"}"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401,
                        "A reassigned GATE_STAFF session must not change a password through Web"));
        assertTrue(passwordChangeSession.isInvalid());
        verify(passwordChangeService, never()).changePassword(any(), any(), any());

        MockHttpSession pageSession = authenticatedManagementSession();
        mockMvc.perform(get("/account/security").session(pageSession))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401,
                        "A reassigned GATE_STAFF session must be rejected from protected Web pages"));
        assertTrue(pageSession.isInvalid());
    }

    @Test
    void disabledAccountCannotContinueUsingWebSession() throws Exception {
        AuthenticatedAccount loginAccount = account();
        AuthenticatedAccount disabledAccount = new AuthenticatedAccount(7L, "gate.staff", UserStatus.DISABLED, null,
                loginAccount.authorities());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(loginAccount, null, loginAccount.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(disabledAccount);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "gate.staff")
                        .param("password", "valid-password-1"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/web/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession authenticatedManagementSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "manager")
                        .param("password", "current-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private AuthenticatedAccount account() {
        return new AuthenticatedAccount(7L, "gate.staff", UserStatus.ACTIVE, null, List.of(
                new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                new SimpleGrantedAuthority("ROLE_MANAGEMENT"),
                new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
    }

    private AuthenticatedAccount gateStaffAccount() {
        return new AuthenticatedAccount(7L, "gate.staff", UserStatus.ACTIVE, null, List.of(
                new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
    }

    private String issueJwt(Long userId, String credentialVersion) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("smart-parking")
                .subject(userId.toString())
                .audience(List.of("smart-parking-api"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofHours(12)))
                .id("test-token")
                .claim("cv", credentialVersion)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

}

@TestConfiguration(proxyBeanMethods = false)
class JwtTestConfiguration {
    @Bean
    JwtProperties testJwtProperties() {
        return new JwtProperties("focused-test-hs256-key-at-least-256-bits", "smart-parking",
                "smart-parking-api", Duration.ofHours(12));
    }
}
