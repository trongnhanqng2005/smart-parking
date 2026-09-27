package vn.edu.huit.smartparking.backend.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.service.AuthenticatedAccount;

class WebLogoutAuditHandlerTests {
    @Test
    void recordsLogoutActorAndRequestMetadataWithoutCredentialOrTokenData() throws Exception {
        UserRepository users = mock(UserRepository.class);
        AuditService audit = mock(AuditService.class);
        AuthRequestDetailsSource detailsSource = new AuthRequestDetailsSource();
        User actor = new User();
        actor.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        AuthenticatedAccount account = new AuthenticatedAccount(7L, "staff", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF")));
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                account, "must-not-be-audited", account.authorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.50");
        request.addHeader("X-Request-ID", "logout-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new WebLogoutAuditHandler(users, audit, detailsSource).onLogoutSuccess(request, response, authentication);

        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        verify(audit).record("AUTH_LOGOUT", "AUTHENTICATION", "7", actor, null,
                "{\"outcome\":\"SUCCESS\"}", "logout-request", "192.0.2.50");
    }

    @Test
    void doesNotAuditAnonymousLogoutRequests() throws Exception {
        UserRepository users = mock(UserRepository.class);
        AuditService audit = mock(AuditService.class);
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new WebLogoutAuditHandler(users, audit, new AuthRequestDetailsSource())
                .onLogoutSuccess(new MockHttpServletRequest(), response, anonymous);

        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        verifyNoInteractions(users, audit);
    }
}
