package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record VehicleRightLifecycleRequest(
        @JsonProperty("effective_at") @NotNull LocalDateTime effectiveAt,
        @JsonProperty("reason") @jakarta.validation.constraints.NotBlank @Size(max = 500) String reason) {}
