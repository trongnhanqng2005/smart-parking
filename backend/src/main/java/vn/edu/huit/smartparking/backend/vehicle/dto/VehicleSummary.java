package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleStatus;

public record VehicleSummary(
        Long id,
        @JsonProperty("plate_number") String plateNumber,
        @JsonProperty("vehicle_category_id") Long vehicleCategoryId,
        String brand,
        VehicleStatus status) {}
