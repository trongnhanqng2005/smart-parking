package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;

public record ResidentDetail(
        Long id,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("identity_number") String identityNumber,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        String phone,
        String email,
        ResidentStatus status,
        @JsonProperty("created_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
        @JsonProperty("updated_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {}
