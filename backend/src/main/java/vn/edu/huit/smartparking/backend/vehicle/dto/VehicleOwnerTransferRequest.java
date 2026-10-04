package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record VehicleOwnerTransferRequest(
        @JsonProperty("from_relation_id") @NotNull @Positive Long fromRelationId,
        @JsonProperty("to_resident_id") @NotNull @Positive Long toResidentId,
        @JsonProperty("effective_at") @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime effectiveAt,
        @NotBlank @Size(max = 500) String reason) {}
