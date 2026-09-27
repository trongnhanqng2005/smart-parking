package vn.edu.huit.smartparking.backend.security.service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validateNewPassword(String currentPassword, String newPassword) {
        if (!isValidNewPassword(newPassword)) {
            throw new PasswordPolicyViolationException("Password does not meet policy");
        }
        if (newPassword.equals(currentPassword)) {
            throw new PasswordPolicyViolationException("New password must differ from current password");
        }
    }

    public static boolean isValidNewPassword(String password) {
        return password != null
                && password.codePointCount(0, password.length()) >= 10
                && isBcryptCompatible(password);
    }

    public static boolean isBcryptCompatible(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
