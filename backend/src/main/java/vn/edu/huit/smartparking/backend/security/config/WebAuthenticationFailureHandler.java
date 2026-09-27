package vn.edu.huit.smartparking.backend.security.config;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import vn.edu.huit.smartparking.backend.security.service.LoginThrottledException;

@Component
public class WebAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        response.sendError(exception instanceof LoginThrottledException
                ? 429
                : HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
    }
}
