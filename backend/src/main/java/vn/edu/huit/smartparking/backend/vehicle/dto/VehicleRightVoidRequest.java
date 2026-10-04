package vn.edu.huit.smartparking.backend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRightVoidRequest(@JsonProperty("reason") @NotBlank @Size(max = 500) String reason) {}
