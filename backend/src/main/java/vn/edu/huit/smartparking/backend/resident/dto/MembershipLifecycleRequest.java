package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record MembershipLifecycleRequest(
        @JsonProperty("effective_at") @NotNull LocalDateTime effectiveAt,
        @NotBlank @Size(max = 500) String reason) {}
