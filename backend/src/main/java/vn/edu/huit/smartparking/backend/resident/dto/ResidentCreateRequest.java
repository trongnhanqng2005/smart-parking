package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ResidentCreateRequest(
        @JsonProperty("full_name") @NotBlank @Size(max = 150) String fullName,
        @JsonProperty("identity_number") @NotBlank @Size(max = 30) String identityNumber,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("phone") @Size(max = 30) String phone,
        @JsonProperty("email") @Size(max = 150) String email) {}
