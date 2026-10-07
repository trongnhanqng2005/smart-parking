package vn.edu.huit.smartparking.backend.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApartmentReactivationRequest(@NotBlank @Size(max = 500) String reason) {}
