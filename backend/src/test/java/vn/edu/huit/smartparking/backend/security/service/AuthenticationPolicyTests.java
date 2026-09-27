package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import vn.edu.huit.smartparking.backend.security.dto.LoginRequest;

class AuthenticationPolicyTests {
    @Test
    void canonicalizesUsernameByTrimmingAndLowercase() {
        assertEquals("gate.staff", UsernameCanonicalizer.canonicalize("  Gate.Staff  "));
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> UsernameCanonicalizer.canonicalize("  "));
    }

    @Test
    void validatesLoginUsernameLengthAfterCanonicalizingWhitespaceAndCase() {
        var factory = Validation.buildDefaultValidatorFactory();
        try {
            Validator validator = factory.getValidator();
            LoginRequest request = new LoginRequest("  " + "A".repeat(100) + "  ", "valid-password-1");

            assertEquals("a".repeat(100), request.username());
            assertTrue(validator.validate(request).isEmpty());
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsLoginUsernameWhenCanonicalValueExceeds100Characters() {
        var factory = Validation.buildDefaultValidatorFactory();
        try {
            Validator validator = factory.getValidator();
            LoginRequest request = new LoginRequest("  " + "A".repeat(101) + "  ", "valid-password-1");

            assertFalse(validator.validate(request).isEmpty());
        } finally {
            factory.close();
        }
    }

    @Test
    void acceptsSupplementaryLoginUsernameAt100CanonicalCodePoints() {
        var factory = Validation.buildDefaultValidatorFactory();
        try {
            Validator validator = factory.getValidator();
            String username = "😀".repeat(100);
            LoginRequest request = new LoginRequest(username, "valid-password-1");

            assertEquals(200, request.username().length());
            assertEquals(100, request.username().codePointCount(0, request.username().length()));
            assertTrue(validator.validate(request).isEmpty());
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsSupplementaryLoginUsernameAt101CanonicalCodePoints() {
        var factory = Validation.buildDefaultValidatorFactory();
        try {
            Validator validator = factory.getValidator();
            LoginRequest request = new LoginRequest("😀".repeat(101), "valid-password-1");

            assertFalse(validator.validate(request).isEmpty());
        } finally {
            factory.close();
        }
    }

    @Test
    void acceptsPasswordAtCharacterAndUtf8ByteLimits() {
        PasswordPolicy.validateNewPassword("previous-password", "é".repeat(36));
    }

    @Test
    void rejectsPasswordsLongerThan72Utf8Bytes() {
        assertThrows(PasswordPolicyViolationException.class,
                () -> PasswordPolicy.validateNewPassword("old-password", "é".repeat(37)));
    }

    @Test
    void rejectsUnchangedPassword() {
        assertThrows(PasswordPolicyViolationException.class,
                () -> PasswordPolicy.validateNewPassword("same-password", "same-password"));
    }

    @Test
    void enforcesTheTenCharacterMinimum() {
        assertThrows(PasswordPolicyViolationException.class,
                () -> PasswordPolicy.validateNewPassword("old-password", "123456789"));
        PasswordPolicy.validateNewPassword("old-password", "1234567890");
    }

    @Test
    void throttlesSixthFailureForCanonicalUsernameAndIpUntilWindowExpires() {
        Instant now = Instant.parse("2026-09-27T10:00:00Z");
        MutableClock clock = new MutableClock(now);
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(clock, 5, Duration.ofMinutes(15));

        for (int attempt = 0; attempt < 5; attempt++) {
            try (LoginAttemptThrottle.Attempt admission = throttle.beginAttempt("  Gate.Staff ", "192.0.2.10")) {
                assertFalse(admission.isBlocked());
                admission.recordFailure();
            }
        }
        try (LoginAttemptThrottle.Attempt blocked = throttle.beginAttempt("GATE.STAFF", "192.0.2.10")) {
            assertTrue(blocked.isBlocked());
        }
        try (LoginAttemptThrottle.Attempt otherIp = throttle.beginAttempt("gate.staff", "192.0.2.11")) {
            assertFalse(otherIp.isBlocked());
        }

        clock.advance(Duration.ofMinutes(15));
        try (LoginAttemptThrottle.Attempt expired = throttle.beginAttempt("gate.staff", "192.0.2.10")) {
            assertFalse(expired.isBlocked());
        }
    }

    @Test
    void concurrentFailuresAreRecordedWithoutExceedingThePerKeyLimit() throws Exception {
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(
                new MutableClock(Instant.parse("2026-09-27T10:00:00Z")), 5, Duration.ofMinutes(15));
        int workers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int attempt = 0; attempt < 100; attempt++) {
                        try (LoginAttemptThrottle.Attempt admission =
                                     throttle.beginAttempt("gate.staff", "192.0.2.10")) {
                            if (!admission.isBlocked()) {
                                admission.recordFailure();
                            }
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            executor.shutdownNow();
        }

        try (LoginAttemptThrottle.Attempt blocked = throttle.beginAttempt("GATE.STAFF", "192.0.2.10")) {
            assertTrue(blocked.isBlocked());
        }
    }

    @Test
    void boundsTrackedKeysAndReclaimsExpiredEntriesAtCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(clock, 5, Duration.ofMinutes(15));

        for (int key = 0; key < LoginAttemptThrottle.MAX_TRACKED_KEYS; key++) {
            try (LoginAttemptThrottle.Attempt admission = throttle.beginAttempt("user." + key, "192.0.2.10")) {
                admission.recordFailure();
            }
        }

        try (LoginAttemptThrottle.Attempt full = throttle.beginAttempt("new.user", "192.0.2.10")) {
            assertTrue(full.isBlocked());
        }
        clock.advance(Duration.ofMinutes(15));
        try (LoginAttemptThrottle.Attempt reclaimed = throttle.beginAttempt("new.user", "192.0.2.10")) {
            assertFalse(reclaimed.isBlocked());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
