package vn.edu.huit.smartparking.backend.security.service;

import java.util.Locale;

public final class UsernameCanonicalizer {
    private UsernameCanonicalizer() {}

    public static String canonicalize(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required");
        }

        String canonical = normalize(username);
        if (canonical.isEmpty() || canonical.codePointCount(0, canonical.length()) > 100) {
            throw new IllegalArgumentException("Invalid username");
        }
        return canonical;
    }

    public static String normalize(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }
}
