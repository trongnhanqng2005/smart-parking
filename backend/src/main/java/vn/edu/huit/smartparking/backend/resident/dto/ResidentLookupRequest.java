package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResidentLookupRequest(
        @JsonProperty("identity_number") @NotBlank @Size(max = 30) String identityNumber) {}
