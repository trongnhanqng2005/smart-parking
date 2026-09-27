package vn.edu.huit.smartparking.backend.security.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;

@Service
public class PasswordChangeService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
        if (user.getStatus() != UserStatus.ACTIVE
                || !PasswordPolicy.isBcryptCompatible(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Authentication failed");
        }
        PasswordPolicy.validateNewPassword(currentPassword, newPassword);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (user.getCredentialChangedAt() != null
                && !now.isAfter(user.getCredentialChangedAt().plusNanos(999))) {
            now = user.getCredentialChangedAt().plusNanos(1_000);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialChangedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        auditService.record("AUTH_PASSWORD_CHANGE", "USER", user.getId().toString(), user, null,
                "{\"outcome\":\"SUCCESS\"}");
    }
}
