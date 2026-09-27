package vn.edu.huit.smartparking.backend.security.config;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class WebAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    public static final String AUTHENTICATED_AT = "SMART_PARKING_AUTHENTICATED_AT";

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        request.getSession(true).setAttribute(AUTHENTICATED_AT, System.currentTimeMillis());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
