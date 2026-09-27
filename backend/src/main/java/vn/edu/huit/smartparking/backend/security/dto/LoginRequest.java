package vn.edu.huit.smartparking.backend.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import vn.edu.huit.smartparking.backend.security.service.UsernameCanonicalizer;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        username = UsernameCanonicalizer.normalize(username);
    }

    @AssertTrue(message = "Username must be at most 100 canonical Unicode code points")
    public boolean isCanonicalUsernameValid() {
        if (username == null || username.isBlank()) {
            return true;
        }
        try {
            UsernameCanonicalizer.canonicalize(username);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
