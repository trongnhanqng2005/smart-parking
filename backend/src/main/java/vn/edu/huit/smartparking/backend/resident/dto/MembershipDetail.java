package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;

public record MembershipDetail(
        Long id,
        @JsonProperty("apartment_id") Long apartmentId,
        @JsonProperty("resident_id") Long residentId,
        @JsonProperty("member_role") MembershipRole memberRole,
        @JsonProperty("valid_from") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validFrom,
        @JsonProperty("valid_to") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime validTo,
        MembershipStatus status,
        @JsonProperty("lifecycle_changed_at") @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime lifecycleChangedAt,
        @JsonProperty("lifecycle_reason") String lifecycleReason,
        @JsonProperty("created_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt) {}
