package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;

public record AuthorizedUserGrantRequest(
        @JsonProperty("vehicle_id") @NotNull @Positive Long vehicleId,
        @JsonProperty("resident_id") @NotNull @Positive Long residentId,
        @JsonProperty("guarantor_type") @NotNull VehicleRelationGuarantorType guarantorType,
        @JsonProperty("guarantor_resident_id") @NotNull @Positive Long guarantorResidentId,
        @JsonProperty("guarantor_apartment_id") @Positive Long guarantorApartmentId,
        @JsonProperty("valid_from") @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validFrom,
        @JsonProperty("valid_to") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validTo,
        @NotBlank @Size(max = 500) String reason) {}
