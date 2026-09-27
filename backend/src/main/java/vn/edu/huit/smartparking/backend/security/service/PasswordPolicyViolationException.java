package vn.edu.huit.smartparking.backend.security.service;

public final class PasswordPolicyViolationException extends IllegalArgumentException {
    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
