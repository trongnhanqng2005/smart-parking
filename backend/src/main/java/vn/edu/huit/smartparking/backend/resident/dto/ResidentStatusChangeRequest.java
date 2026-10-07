package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;

public record ResidentStatusChangeRequest(
        @JsonProperty("status") @NotNull ResidentStatus status,
        @JsonProperty("reason") @NotBlank @Size(max = 500) String reason,
        @JsonProperty("membership_actions") List<@Valid MembershipAction> membershipActions,
        @JsonProperty("vehicle_right_actions") List<@Valid VehicleRightAction> vehicleRightActions) {

    public record MembershipAction(
            @JsonProperty("membership_id") @NotNull @Positive Long membershipId,
            @JsonProperty("action") @NotNull RelationLifecycleAction action,
            @JsonProperty("effective_at") @NotNull LocalDateTime effectiveAt,
            @JsonProperty("reason") @NotBlank @Size(max = 500) String reason) {}

    public record VehicleRightAction(
            @JsonProperty("relation_id") @NotNull @Positive Long relationId,
            @JsonProperty("action") @NotNull RelationLifecycleAction action,
            @JsonProperty("effective_at") @NotNull LocalDateTime effectiveAt,
            @JsonProperty("reason") @NotBlank @Size(max = 500) String reason) {}
}
