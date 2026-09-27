package vn.edu.huit.smartparking.backend.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smart-parking.security.jwt")
public record JwtProperties(String secret, String issuer, String audience, Duration accessTokenTtl) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT issuer, audience, and positive access-token lifetime are required");
        }
    }
}
