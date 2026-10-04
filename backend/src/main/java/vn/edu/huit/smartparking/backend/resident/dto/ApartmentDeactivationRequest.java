package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApartmentDeactivationRequest(
        @NotBlank @Size(max = 500) String reason,
        @JsonProperty("membership_ends") List<@Valid MembershipEndRequest> membershipEnds) {}
