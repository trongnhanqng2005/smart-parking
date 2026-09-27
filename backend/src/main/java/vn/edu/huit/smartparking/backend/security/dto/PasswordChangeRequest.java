package vn.edu.huit.smartparking.backend.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @JsonProperty("current_password") @NotBlank @Size(max = 72) String currentPassword,
        @JsonProperty("new_password") @NotBlank @Size(max = 72) String newPassword) {}
