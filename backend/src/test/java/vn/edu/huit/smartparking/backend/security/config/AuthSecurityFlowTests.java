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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest({AuthController.class, AccountSecurityPageController.class, LoginPageController.class, ManagementHomeController.class})
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
