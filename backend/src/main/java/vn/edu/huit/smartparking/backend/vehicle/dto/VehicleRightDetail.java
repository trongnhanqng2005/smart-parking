package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;

public record VehicleRightDetail(
        Long id,
        @JsonProperty("vehicle_id") Long vehicleId,
        @JsonProperty("resident_id") Long residentId,
        @JsonProperty("relation_type") VehicleRelationType relationType,
        @JsonProperty("guarantor_type") VehicleRelationGuarantorType guarantorType,
        @JsonProperty("guarantor_resident_id") Long guarantorResidentId,
        @JsonProperty("guarantor_apartment_id") Long guarantorApartmentId,
        @JsonProperty("valid_from") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validFrom,
        @JsonProperty("valid_to") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validTo,
        VehicleRelationStatus status,
        @JsonProperty("lifecycle_changed_at") @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime lifecycleChangedAt,
        @JsonProperty("lifecycle_reason") String lifecycleReason,
        @JsonProperty("created_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt) {}
