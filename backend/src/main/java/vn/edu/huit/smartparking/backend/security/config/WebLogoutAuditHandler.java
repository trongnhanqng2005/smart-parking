package vn.edu.huit.smartparking.backend.security.config;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;

@Component
public class WebLogoutAuditHandler implements LogoutSuccessHandler {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthRequestDetailsSource detailsSource;

    public WebLogoutAuditHandler(
            UserRepository userRepository, AuditService auditService, AuthRequestDetailsSource detailsSource) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.detailsSource = detailsSource;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            User actor = userRepository.findById(account.userId()).orElse(null);
            var requestDetails = detailsSource.buildDetails(request);
            auditService.record("AUTH_LOGOUT", "AUTHENTICATION", actor == null ? null : actor.getId().toString(),
                    actor, null, "{\"outcome\":\"SUCCESS\"}",
                    requestDetails.requestId(), requestDetails.remoteAddress());
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
