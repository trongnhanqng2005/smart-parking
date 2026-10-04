package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;

public record ResidentSummary(Long id, @JsonProperty("full_name") String fullName, ResidentStatus status) {}
