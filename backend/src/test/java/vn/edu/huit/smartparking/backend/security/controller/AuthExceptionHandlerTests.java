package vn.edu.huit.smartparking.backend.security.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import vn.edu.huit.smartparking.backend.security.service.PasswordPolicyViolationException;

class AuthExceptionHandlerTests {
    @Test
    void onlyMapsPasswordPolicyViolationsToBadRequest() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(AuthExceptionHandler.class);

        assertNotNull(resolver.resolveMethodByExceptionType(PasswordPolicyViolationException.class));
        assertNull(resolver.resolveMethodByExceptionType(IllegalArgumentException.class));
    }
}
