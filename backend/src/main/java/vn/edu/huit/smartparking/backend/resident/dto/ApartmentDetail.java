package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;

public record ApartmentDetail(
        Long id,
        String building,
        @JsonProperty("apartment_code") String apartmentCode,
        @JsonProperty("floor_no") Integer floorNo,
        ApartmentStatus status,
        @JsonProperty("created_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
        @JsonProperty("updated_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {}
