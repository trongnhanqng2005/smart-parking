package vn.edu.huit.smartparking.backend.security.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;
import vn.edu.huit.smartparking.backend.security.service.JwtTokenService;
import vn.edu.huit.smartparking.backend.security.service.SecurityAccountService;

@Component
public class WebSessionSecurityFilter extends OncePerRequestFilter {
    private static final long ABSOLUTE_LIFETIME_MILLIS = Duration.ofHours(8).toMillis();
    private final SecurityAccountService accountService;

    public WebSessionSecurityFilter(SecurityAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount previous)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Object authenticatedAt = session == null ? null : session.getAttribute(WebAuthenticationSuccessHandler.AUTHENTICATED_AT);
        if (!(authenticatedAt instanceof Long start)
                || System.currentTimeMillis() - start >= ABSOLUTE_LIFETIME_MILLIS) {
            reject(response, session);
            return;
        }

        AuthenticatedAccount current;
        try {
            current = accountService.loadUserById(previous.userId());
        } catch (RuntimeException exception) {
            reject(response, session);
            return;
        }
        if (current.status() != UserStatus.ACTIVE
                || !Objects.equals(JwtTokenService.credentialVersion(previous.credentialChangedAt()),
                        JwtTokenService.credentialVersion(current.credentialChangedAt()))) {
            reject(response, session);
            return;
        }

        UsernamePasswordAuthenticationToken refreshed = new UsernamePasswordAuthenticationToken(
                current, null, current.authorities());
        refreshed.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(refreshed);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpSession session)
            throws IOException {
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
