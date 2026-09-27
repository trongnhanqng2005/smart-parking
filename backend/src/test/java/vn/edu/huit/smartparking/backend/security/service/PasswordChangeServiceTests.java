package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;

class PasswordChangeServiceTests {
    @Test
    void changesPasswordAndAdvancesCredentialTimestampAtMicrosecondPrecision() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        LocalDateTime before = LocalDateTime.parse("2026-09-27T10:00:00.123456");
        User user = new User();
        user.setId(2L);
        user.setUsername("management");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(encoder.encode("current-password"));
        user.setCredentialChangedAt(before);
        UserRepository users = mock(UserRepository.class);
        org.mockito.Mockito.when(users.findById(2L)).thenReturn(java.util.Optional.of(user));
        AuditService audit = mock(AuditService.class);
        PasswordChangeService service = new PasswordChangeService(
                users, encoder, audit, Clock.fixed(before.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

        service.changePassword(2L, "current-password", "replacement-password");

        assertTrue(encoder.matches("replacement-password", user.getPasswordHash()));
        assertEquals(before.plusNanos(1_000), user.getCredentialChangedAt());
        verify(audit).record("AUTH_PASSWORD_CHANGE", "USER", "2", user, null,
                "{\"outcome\":\"SUCCESS\"}");
    }
}
