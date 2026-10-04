package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        @JsonProperty("total_items") long totalItems) {}
