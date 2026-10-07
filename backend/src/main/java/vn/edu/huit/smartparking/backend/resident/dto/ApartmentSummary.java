package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;

public record ApartmentSummary(
        Long id,
        String building,
        @JsonProperty("apartment_code") String apartmentCode,
        @JsonProperty("floor_no") Integer floorNo,
        ApartmentStatus status) {}
