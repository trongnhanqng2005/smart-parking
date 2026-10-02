package vn.edu.huit.smartparking.backend.security.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;

@Component
public class AccountAuthenticationProvider implements AuthenticationProvider {
    private final UserRepository userRepository;
    private final SecurityAccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptThrottle throttle;
    private final AuditService auditService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AccountAuthenticationProvider(
            UserRepository userRepository,
            SecurityAccountService accountService,
            PasswordEncoder passwordEncoder,
            LoginAttemptThrottle throttle,
            AuditService auditService,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.auditService = auditService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String suppliedUsername = authentication.getPrincipal() == null ? "" : authentication.getPrincipal().toString();
        String username;
        try {
            username = UsernameCanonicalizer.canonicalize(suppliedUsername);
        } catch (IllegalArgumentException exception) {
            recordAuthentication("AUTH_LOGIN_FAILURE", null, null, authentication,
                    "{\"outcome\":\"DENIED\"}");
            throw new BadCredentialsException("Authentication failed");
        }
        String sourceIp = sourceIp(authentication);

        try (LoginAttemptThrottle.Attempt attempt = throttle.beginAttempt(username, sourceIp)) {
            if (attempt.isBlocked()) {
                recordAuthentication("AUTH_LOGIN_THROTTLED", username, null, authentication,
                        "{\"outcome\":\"THROTTLED\"}");
                throw new LoginThrottledException();
            }

            User user = userRepository.findByUsername(username).orElse(null);
            String rawPassword = authentication.getCredentials() instanceof String password ? password : "";
            boolean passwordMatches = PasswordPolicy.isBcryptCompatible(rawPassword)
                    ? passwordEncoder.matches(rawPassword, user == null ? dummyPasswordHash : user.getPasswordHash())
                    : passwordEncoder.matches("", dummyPasswordHash);

            if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordMatches) {
                attempt.recordFailure();
                recordAuthentication("AUTH_LOGIN_FAILURE", username, null, authentication,
                        "{\"outcome\":\"DENIED\"}");
                throw new BadCredentialsException("Authentication failed");
            }

            AuthenticatedAccount principal;
            try {
                principal = accountService.load(user);
            } catch (UsernameNotFoundException exception) {
                attempt.recordFailure();
                recordAuthentication("AUTH_LOGIN_FAILURE", username, null, authentication,
                        "{\"outcome\":\"DENIED\"}");
                throw new BadCredentialsException("Authentication failed");
            }
            if (isWebAuthentication(authentication) && !hasManagementRole(principal)) {
                attempt.recordFailure();
                recordAuthentication("AUTH_LOGIN_FAILURE", user.getId().toString(), user, authentication,
                        "{\"outcome\":\"DENIED\",\"reason\":\"WEB_CHANNEL_ROLE_DENIED\"}");
                throw new BadCredentialsException("Authentication failed");
            }

            attempt.recordSuccess();
            user.setLastLoginAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            userRepository.save(user);
            recordAuthentication("AUTH_LOGIN_SUCCESS", user.getId().toString(), user, authentication,
                    "{\"outcome\":\"SUCCESS\"}");
            UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.authorities());
            result.setDetails(authentication.getDetails());
            return result;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean isWebAuthentication(Authentication authentication) {
        return !(authentication.getDetails() instanceof AuthRequestDetails requestDetails)
                || requestDetails.channel() != AuthRequestDetails.Channel.REST;
    }

    private boolean hasManagementRole(AuthenticatedAccount account) {
        return account.authorities().stream()
                .anyMatch(authority -> "ROLE_MANAGEMENT".equals(authority.getAuthority()));
    }

    private String sourceIp(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof AuthRequestDetails requestDetails && requestDetails.remoteAddress() != null) {
            return requestDetails.remoteAddress();
        }
        return details instanceof WebAuthenticationDetails webDetails ? webDetails.getRemoteAddress() : "unknown";
    }

    private void recordAuthentication(
            String action, String entityId, User actor, Authentication authentication, String outcome) {
        Object details = authentication.getDetails();
        String requestId = details instanceof AuthRequestDetails requestDetails ? requestDetails.requestId() : null;
        String remoteAddress = details instanceof AuthRequestDetails requestDetails
                ? requestDetails.remoteAddress()
                : sourceIp(authentication);
        auditService.record(action, "AUTHENTICATION", entityId, actor, null, outcome, requestId, remoteAddress);
    }
}
