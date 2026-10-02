package vn.edu.huit.smartparking.backend.security.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import vn.edu.huit.smartparking.backend.security.service.AuthRequestDetails;

@Component
public class AuthRequestDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, AuthRequestDetails> {
    @Override
    public AuthRequestDetails buildDetails(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null && requestId.length() > 100) {
            requestId = null;
        }
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        AuthRequestDetails.Channel channel = requestPath.startsWith("/api/")
                ? AuthRequestDetails.Channel.REST
                : AuthRequestDetails.Channel.WEB;
        return new AuthRequestDetails(request.getRemoteAddr(), requestId, channel);
    }
}
