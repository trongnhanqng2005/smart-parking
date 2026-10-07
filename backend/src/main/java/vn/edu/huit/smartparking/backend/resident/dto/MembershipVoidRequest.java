package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MembershipVoidRequest(@JsonProperty("reason") @NotBlank @Size(max = 500) String reason) {}
