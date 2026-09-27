package vn.edu.huit.smartparking.backend.security.service;

import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;

@Component
public class CurrentJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    private final SecurityAccountService accountService;

    public CurrentJwtAuthenticationConverter(SecurityAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        try {
            AuthenticatedAccount account = accountService.loadUserById(Long.valueOf(jwt.getSubject()));
            String tokenCredentialVersion = jwt.getClaimAsString("cv");
            if (account.status() != UserStatus.ACTIVE
                    || !Objects.equals(tokenCredentialVersion, JwtTokenService.credentialVersion(account.credentialChangedAt()))) {
                throw new BadCredentialsException("Authentication failed");
            }
            return new JwtAuthenticationToken(jwt, account.authorities(), account.username()) {
                @Override
                public Object getPrincipal() {
                    return account;
                }
            };
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Authentication failed", exception);
        }
    }
}
