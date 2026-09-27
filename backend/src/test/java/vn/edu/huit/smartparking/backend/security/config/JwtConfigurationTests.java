package vn.edu.huit.smartparking.backend.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.security.service.JwtTokenService;

class JwtConfigurationTests {
    @Test
    void rejectsSigningSecretsShorterThan256Bits() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties properties = new JwtProperties("s".repeat(31), "smart-parking", "smart-parking-api", Duration.ofHours(12));

        assertThrows(IllegalStateException.class, () -> configuration.jwtSecretKey(properties));
    }

    @Test
    void secretRotationInvalidatesPreviouslySignedTokens() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties original = properties("a".repeat(32));
        var originalKey = configuration.jwtSecretKey(original);
        JwtTokenService tokenService = new JwtTokenService(
                configuration.jwtEncoder(originalKey), original, Clock.systemUTC());
        String token = tokenService.issue(new AuthenticatedAccount(
                9L, "staff", UserStatus.ACTIVE, null, List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF")))).value();

        assertEquals("9", configuration.jwtDecoder(originalKey, original).decode(token).getSubject());
        JwtProperties rotated = properties("b".repeat(32));
        JwtDecoder rotatedDecoder = configuration.jwtDecoder(configuration.jwtSecretKey(rotated), rotated);
        assertThrows(JwtException.class, () -> rotatedDecoder.decode(token));
    }

    @Test
    void rejectsExpiredTokenWithoutLeeway() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties properties = properties("a".repeat(32));
        var key = configuration.jwtSecretKey(properties);
        String token = signedToken(configuration.jwtEncoder(key), "smart-parking", "smart-parking-api",
                Instant.now().minusSeconds(20), Instant.now().minusSeconds(10));

        assertThrows(JwtException.class, () -> configuration.jwtDecoder(key, properties).decode(token));
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties properties = properties("a".repeat(32));
        var key = configuration.jwtSecretKey(properties);
        JwtDecoder decoder = configuration.jwtDecoder(key, properties);
        Instant now = Instant.now();

        String wrongIssuer = signedToken(configuration.jwtEncoder(key), "other-issuer", "smart-parking-api",
                now, now.plusSeconds(60));
        String wrongAudience = signedToken(configuration.jwtEncoder(key), "smart-parking", "other-api",
                now, now.plusSeconds(60));

        assertThrows(JwtException.class, () -> decoder.decode(wrongIssuer));
        assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
    }

    @Test
    void rejectsTokenWithInvalidSignature() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties trustedProperties = properties("a".repeat(32));
        var trustedKey = configuration.jwtSecretKey(trustedProperties);
        JwtProperties attackerProperties = properties("b".repeat(32));
        var attackerKey = configuration.jwtSecretKey(attackerProperties);
        String token = signedToken(configuration.jwtEncoder(attackerKey), "smart-parking", "smart-parking-api",
                Instant.now(), Instant.now().plusSeconds(60));

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(trustedKey, trustedProperties).decode(token));
    }

    private String signedToken(JwtEncoder encoder, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("9")
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, "smart-parking", "smart-parking-api", Duration.ofHours(12));
    }
}
