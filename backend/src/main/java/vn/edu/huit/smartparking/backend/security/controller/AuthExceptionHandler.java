package vn.edu.huit.smartparking.backend.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;
import vn.edu.huit.smartparking.backend.security.service.PasswordPolicyViolationException;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<AuthError> invalidPassword(PasswordPolicyViolationException exception) {
        return ResponseEntity.badRequest().body(new AuthError("PASSWORD_POLICY_VIOLATION", "Password does not meet policy"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthError> invalidCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthError("AUTHENTICATION_FAILED", "Authentication failed"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthError> invalidRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new AuthError("INVALID_REQUEST", "Request validation failed"));
    }
}
