package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import vn.edu.huit.smartparking.backend.security.config.JwtProperties;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;

class JwtTokenServiceTests {
    @Test
    void issuesTwelveHourBearerTokenWithoutPermissionOrShiftAuthorityClaims() {
        Instant now = Instant.parse("2026-09-27T10:00:00Z");
        AtomicReference<JwtEncoderParameters> captured = new AtomicReference<>();
        JwtEncoder encoder = parameters -> {
            captured.set(parameters);
            Map<String, Object> claims = parameters.getClaims().getClaims();
            return Jwt.withTokenValue("encoded-jwt").header("alg", "HS256")
                    .claims(values -> values.putAll(claims)).build();
        };
        JwtTokenService service = new JwtTokenService(
                encoder,
                new JwtProperties("s".repeat(32), "smart-parking", "smart-parking-api", Duration.ofHours(12)),
                Clock.fixed(now, ZoneOffset.UTC));
        AuthenticatedAccount account = new AuthenticatedAccount(41L, "manager", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGEMENT")));

        JwtTokenService.IssuedJwt issued = service.issue(account);
        Map<String, Object> claims = captured.get().getClaims().getClaims();

        assertEquals("encoded-jwt", issued.value());
        assertEquals(43_200, issued.expiresIn());
        assertEquals("41", claims.get("sub"));
        assertEquals("smart-parking", claims.get("iss"));
        assertEquals(List.of("smart-parking-api"), claims.get("aud"));
        assertEquals(now, claims.get("iat"));
        assertEquals(now.plus(Duration.ofHours(12)), claims.get("exp"));
        assertEquals("", claims.get("cv"));
        assertFalse(claims.containsKey("permissions"));
        assertFalse(claims.containsKey("shift"));
        assertFalse(claims.containsKey("role"));
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("password_hash"));
    }

    @Test
    void credentialVersionChangesWhenPasswordTimestampChanges() {
        LocalDateTime before = LocalDateTime.parse("2026-09-27T10:00:00.123456");
        LocalDateTime after = before.plusNanos(1_000);

        assertFalse(JwtTokenService.credentialVersion(before).equals(JwtTokenService.credentialVersion(after)));
    }
}
