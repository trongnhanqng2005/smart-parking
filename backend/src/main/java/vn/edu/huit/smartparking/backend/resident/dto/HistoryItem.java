package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record HistoryItem(
        String action,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime at,
        String reason,
        @JsonProperty("actor_user_id") Long actorUserId,
        @JsonProperty("subject_id") Long subjectId) {}
