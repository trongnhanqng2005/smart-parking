package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record VehicleOwnerAssignmentRequest(
        @JsonProperty("resident_id") @NotNull @Positive Long residentId,
        @JsonProperty("valid_from") @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validFrom,
        @JsonProperty("valid_to") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validTo,
        @NotBlank @Size(max = 500) String reason) {}
