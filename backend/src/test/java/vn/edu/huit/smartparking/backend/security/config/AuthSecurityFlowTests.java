package vn.edu.huit.smartparking.backend.security.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
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
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.controller.AuthController;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.security.service.CurrentJwtAuthenticationConverter;
import vn.edu.huit.smartparking.backend.security.service.JwtTokenService;
import vn.edu.huit.smartparking.backend.security.service.PasswordChangeService;
import vn.edu.huit.smartparking.backend.security.service.PasswordPolicyViolationException;
import vn.edu.huit.smartparking.backend.security.service.SecurityAccountService;

@WebMvcTest(AuthController.class)
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

    @Test
    void restLoginDoesNotRequireCsrfAndReturnsBearerContract() throws Exception {
        AuthenticatedAccount account = account();
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
    }

    @Test
    void webStateChangingRequestRequiresCsrf() throws Exception {
        mockMvc.perform(post("/web/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"old-password\",\"new_password\":\"new-password-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webSessionUsesThirtyMinuteIdleTimeout() {
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofMinutes(30), configuredIdleTimeout);
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
        AuthenticatedAccount loginAccount = account();
        AuthenticatedAccount currentAccount = new AuthenticatedAccount(7L, "gate.staff", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(loginAccount, null, loginAccount.authorities()));
        when(accountService.loadUserById(7L)).thenReturn(currentAccount);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "gate.staff")
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

    private AuthenticatedAccount account() {
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
