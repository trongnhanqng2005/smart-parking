package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;

class AccountAuthenticationProviderTests {
    @Test
    void authenticatesCanonicalUsernameOnlyForActiveUserAndUpdatesLastLogin() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        User user = user("gate.staff", UserStatus.ACTIVE, encoder.encode("valid-password-1"));
        UserRepository users = mock(UserRepository.class);
        SecurityAccountService accounts = mock(SecurityAccountService.class);
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(Clock.systemUTC(), 5, java.time.Duration.ofMinutes(15));
        AuditService audit = mock(AuditService.class);
        when(users.findByUsername("gate.staff")).thenReturn(Optional.of(user));
        when(accounts.load(user)).thenReturn(new AuthenticatedAccount(3L, "gate.staff", UserStatus.ACTIVE, null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"))));

        AccountAuthenticationProvider provider = new AccountAuthenticationProvider(
                users, accounts, encoder, throttle, audit,
                Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
        UsernamePasswordAuthenticationToken request = UsernamePasswordAuthenticationToken.unauthenticated(
                " Gate.Staff ", "valid-password-1");
        request.setDetails(new AuthRequestDetails("192.0.2.10", "request-7"));
        Authentication result = provider.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertEquals("gate.staff", ((AuthenticatedAccount) result.getPrincipal()).username());
        assertEquals(Instant.parse("2026-09-27T10:00:00Z"), user.getLastLoginAt().toInstant(ZoneOffset.UTC));
        verify(audit).record("AUTH_LOGIN_SUCCESS", "AUTHENTICATION", "3", user, null,
                "{\"outcome\":\"SUCCESS\"}", "request-7", "192.0.2.10");
    }

    @Test
    void deniesNonActiveAccountWithGenericCredentialsError() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        User user = user("gate.staff", UserStatus.DISABLED, encoder.encode("valid-password-1"));
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("gate.staff")).thenReturn(Optional.of(user));
        AuditService audit = mock(AuditService.class);
        AccountAuthenticationProvider provider = new AccountAuthenticationProvider(
                users, mock(SecurityAccountService.class), encoder,
                new LoginAttemptThrottle(Clock.systemUTC(), 5, java.time.Duration.ofMinutes(15)),
                audit, Clock.systemUTC());

        BadCredentialsException error = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                        "gate.staff", "valid-password-1")));

        assertEquals("Authentication failed", error.getMessage());
        verify(audit).record("AUTH_LOGIN_FAILURE", "AUTHENTICATION", "gate.staff", null, null,
                "{\"outcome\":\"DENIED\"}", null, "unknown");
    }

    @Test
    void admitsAtMostFiveConcurrentFailuresForOneUsernameAndIp() throws Exception {
        int attempts = 6;
        UserRepository users = mock(UserRepository.class);
        User user = user("gate.staff", UserStatus.ACTIVE, "stored-hash");
        when(users.findByUsername("gate.staff")).thenReturn(Optional.of(user));
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any(CharSequence.class))).thenReturn("dummy-hash");
        AtomicInteger verifiedCredentials = new AtomicInteger();
        CyclicBarrier simultaneousVerification = new CyclicBarrier(attempts);
        when(encoder.matches(any(CharSequence.class), eq("stored-hash"))).thenAnswer(invocation -> {
            verifiedCredentials.incrementAndGet();
            try {
                simultaneousVerification.await(2, TimeUnit.SECONDS);
            } catch (BrokenBarrierException | TimeoutException ignored) {
                // A serialized attempt breaks the barrier; later attempts still verify in turn.
            }
            return false;
        });
        AccountAuthenticationProvider provider = provider(users, encoder,
                new LoginAttemptThrottle(Clock.systemUTC(), 5, Duration.ofMinutes(15)));
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<AuthenticationException>> results = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return authenticateFailure(provider, "wrong-password", "192.0.2.10");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int credentialFailures = 0;
            int throttled = 0;
            for (Future<AuthenticationException> result : results) {
                AuthenticationException exception = result.get(10, TimeUnit.SECONDS);
                if (exception instanceof BadCredentialsException) {
                    credentialFailures++;
                } else if (exception instanceof LoginThrottledException) {
                    throttled++;
                }
            }

            assertEquals(5, verifiedCredentials.get());
            assertEquals(5, credentialFailures);
            assertEquals(1, throttled);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void authenticatesAnUnrelatedKeyWhileAnotherKeyIsVerifyingCredentials() throws Exception {
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("gate.staff")).thenReturn(
                Optional.of(user("gate.staff", UserStatus.ACTIVE, "stored-hash")));
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any(CharSequence.class))).thenReturn("dummy-hash");
        CountDownLatch firstKeyVerifying = new CountDownLatch(1);
        CountDownLatch releaseFirstKey = new CountDownLatch(1);
        when(encoder.matches(any(CharSequence.class), eq("stored-hash"))).thenAnswer(invocation -> {
            if ("blocked-password".contentEquals((CharSequence) invocation.getArgument(0))) {
                firstKeyVerifying.countDown();
                releaseFirstKey.await();
            }
            return false;
        });
        AccountAuthenticationProvider provider = provider(users, encoder,
                new LoginAttemptThrottle(Clock.systemUTC(), 5, Duration.ofMinutes(15)));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthenticationException> first = executor.submit(
                    () -> authenticateFailure(provider, "blocked-password", "192.0.2.10"));
            assertTrue(firstKeyVerifying.await(5, TimeUnit.SECONDS));

            Future<AuthenticationException> unrelated = executor.submit(
                    () -> authenticateFailure(provider, "other-password", "192.0.2.11"));
            assertTrue(unrelated.get(2, TimeUnit.SECONDS) instanceof BadCredentialsException);
            assertTrue(!first.isDone());
        } finally {
            releaseFirstKey.countDown();
            executor.shutdownNow();
        }
    }

    private AccountAuthenticationProvider provider(
            UserRepository users, PasswordEncoder encoder, LoginAttemptThrottle throttle) {
        return new AccountAuthenticationProvider(users, mock(SecurityAccountService.class), encoder,
                throttle, mock(AuditService.class), Clock.systemUTC());
    }

    private AuthenticationException authenticateFailure(
            AccountAuthenticationProvider provider, String password, String sourceIp) {
        UsernamePasswordAuthenticationToken request = UsernamePasswordAuthenticationToken.unauthenticated(
                "gate.staff", password);
        request.setDetails(new AuthRequestDetails(sourceIp, null));
        try {
            provider.authenticate(request);
            return null;
        } catch (AuthenticationException exception) {
            return exception;
        }
    }

    private User user(String username, UserStatus status, String passwordHash) {
        User user = new User();
        user.setId(3L);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setStatus(status);
        return user;
    }
}
