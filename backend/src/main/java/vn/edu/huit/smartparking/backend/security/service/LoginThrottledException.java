package vn.edu.huit.smartparking.backend.security.service;

import org.springframework.security.core.AuthenticationException;

public class LoginThrottledException extends AuthenticationException {
    public LoginThrottledException() {
        super("Authentication failed");
    }
}
