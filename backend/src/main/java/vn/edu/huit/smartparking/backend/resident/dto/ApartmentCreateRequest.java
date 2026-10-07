package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApartmentCreateRequest(
        @JsonProperty("building") @NotBlank @Size(max = 100) String building,
        @JsonProperty("apartment_code") @NotBlank @Size(max = 50) String apartmentCode,
        @JsonProperty("floor_no") Integer floorNo) {}
