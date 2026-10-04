package vn.edu.huit.smartparking.backend.resident.service;

import vn.edu.huit.smartparking.backend.resident.dto.ResidentDetail;

public record ResidentCreateResult(ResidentDetail resident, boolean created) {}
