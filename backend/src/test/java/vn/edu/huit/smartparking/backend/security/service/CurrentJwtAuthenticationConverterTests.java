package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;

class CurrentJwtAuthenticationConverterTests {
    @Test
    void acceptsNewJwtWithUnchangedInitialCredentialVersion() {
        SecurityAccountService accountService = mock(SecurityAccountService.class);
        AuthenticatedAccount current = new AuthenticatedAccount(5L, "staff", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF")));
        when(accountService.loadUserById(5L)).thenReturn(current);
        Jwt token = Jwt.withTokenValue("signed")
                .header("alg", "HS256")
                .subject("5")
                .claim("cv", "")
                .build();

        assertEquals(current, new CurrentJwtAuthenticationConverter(accountService).convert(token).getPrincipal());
    }

    @Test
    void loadsCurrentAuthoritiesFromDatabaseInsteadOfTokenClaims() {
        SecurityAccountService accountService = mock(SecurityAccountService.class);
        LocalDateTime changedAt = LocalDateTime.parse("2026-09-27T10:00:00.123456");
        AuthenticatedAccount current = new AuthenticatedAccount(5L, "staff", UserStatus.ACTIVE, changedAt,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                        new SimpleGrantedAuthority("SECURITY_CHANGE_OWN_PASSWORD")));
        when(accountService.loadUserById(5L)).thenReturn(current);
        Jwt token = Jwt.withTokenValue("signed")
                .header("alg", "HS256")
                .subject("5")
                .claim("cv", JwtTokenService.credentialVersion(changedAt))
                .claim("role", "MANAGEMENT")
                .claim("permissions", List.of("SECURITY_ADMIN"))
                .build();

        var authentication = new CurrentJwtAuthenticationConverter(accountService).convert(token);

        assertEquals(current, authentication.getPrincipal());
        assertEquals(current.authorities(), authentication.getAuthorities());
    }

    @Test
    void rejectsJwtIssuedBeforeCredentialChange() {
        SecurityAccountService accountService = mock(SecurityAccountService.class);
        AuthenticatedAccount current = new AuthenticatedAccount(5L, "staff", UserStatus.ACTIVE,
                LocalDateTime.parse("2026-09-27T10:00:00.123456"), List.of());
        when(accountService.loadUserById(5L)).thenReturn(current);
        Jwt oldToken = Jwt.withTokenValue("signed")
                .header("alg", "HS256")
                .subject("5")
                .claim("cv", "")
                .build();

        assertThrows(BadCredentialsException.class,
                () -> new CurrentJwtAuthenticationConverter(accountService).convert(oldToken));
    }

    @Test
    void rejectsTokenAfterAccountIsDisabled() {
        SecurityAccountService accountService = mock(SecurityAccountService.class);
        AuthenticatedAccount disabled = new AuthenticatedAccount(5L, "staff", UserStatus.DISABLED, null, List.of());
        when(accountService.loadUserById(5L)).thenReturn(disabled);
        Jwt token = Jwt.withTokenValue("signed")
                .header("alg", "HS256")
                .subject("5")
                .claim("cv", "")
                .build();

        assertThrows(BadCredentialsException.class,
                () -> new CurrentJwtAuthenticationConverter(accountService).convert(token));
    }
}
