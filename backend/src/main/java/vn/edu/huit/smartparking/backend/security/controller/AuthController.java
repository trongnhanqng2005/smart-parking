package vn.edu.huit.smartparking.backend.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;
import vn.edu.huit.smartparking.backend.security.dto.LoginRequest;
import vn.edu.huit.smartparking.backend.security.dto.PasswordChangeRequest;
import vn.edu.huit.smartparking.backend.security.dto.TokenResponse;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.security.config.AuthRequestDetailsSource;
import vn.edu.huit.smartparking.backend.security.service.JwtTokenService;
import vn.edu.huit.smartparking.backend.security.service.LoginThrottledException;
import vn.edu.huit.smartparking.backend.security.service.PasswordChangeService;

@RestController
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final PasswordChangeService passwordChangeService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthRequestDetailsSource detailsSource;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            PasswordChangeService passwordChangeService,
            UserRepository userRepository,
            AuditService auditService,
            AuthRequestDetailsSource detailsSource) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.passwordChangeService = passwordChangeService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.detailsSource = detailsSource;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken login = UsernamePasswordAuthenticationToken.unauthenticated(
                request.username(), request.password());
        login.setDetails(detailsSource.buildDetails(httpRequest));
        try {
            Authentication authentication = authenticationManager.authenticate(login);
            AuthenticatedAccount account = (AuthenticatedAccount) authentication.getPrincipal();
            JwtTokenService.IssuedJwt issued = jwtTokenService.issue(account);
            return ResponseEntity.ok(new TokenResponse(issued.value(), "Bearer", issued.expiresIn()));
        } catch (LoginThrottledException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthError("LOGIN_THROTTLED", "Authentication failed"));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthError("AUTHENTICATION_FAILED", "Authentication failed"));
        }
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest httpRequest) {
        User actor = userRepository.findById(account.userId()).orElse(null);
        var requestDetails = detailsSource.buildDetails(httpRequest);
        auditService.record("AUTH_LOGOUT", "AUTHENTICATION", account.userId().toString(), actor, null,
                "{\"outcome\":\"SUCCESS\"}", requestDetails.requestId(), requestDetails.remoteAddress());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/change-password")
    @PreAuthorize("hasAuthority('SECURITY_CHANGE_OWN_PASSWORD')")
    public ResponseEntity<Void> changeRestPassword(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.changePassword(account.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/web/auth/change-password")
    @PreAuthorize("hasAuthority('SECURITY_CHANGE_OWN_PASSWORD')")
    public ResponseEntity<Void> changeWebPassword(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest httpRequest) {
        passwordChangeService.changePassword(account.userId(), request.currentPassword(), request.newPassword());
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
