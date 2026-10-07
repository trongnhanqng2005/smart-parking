package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;

public record MembershipCreateRequest(
        @JsonProperty("apartment_id") @NotNull Long apartmentId,
        @JsonProperty("resident_id") @NotNull Long residentId,
        @JsonProperty("member_role") @NotNull MembershipRole memberRole,
        @JsonProperty("valid_from") @NotNull LocalDateTime validFrom,
        @JsonProperty("valid_to") LocalDateTime validTo,
        @NotBlank @Size(max = 500) String reason) {}
