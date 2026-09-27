package vn.edu.huit.smartparking.backend.security.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.edu.huit.smartparking.backend.gate.entity.WorkShift;
import vn.edu.huit.smartparking.backend.gate.enums.ShiftStatus;
import vn.edu.huit.smartparking.backend.gate.repository.WorkShiftRepository;

@Service
public class ShiftAuthorizationService {
    private final WorkShiftRepository workShiftRepository;

    public ShiftAuthorizationService(WorkShiftRepository workShiftRepository) {
        this.workShiftRepository = workShiftRepository;
    }

    public WorkShift requireAssignedOpenShift(Long userId, Long shiftId, Long laneId) {
        return workShiftRepository.findByIdAndStaffUser_IdAndLane_IdAndStatus(
                        shiftId, userId, laneId, ShiftStatus.OPEN)
                .orElseThrow(() -> new AccessDeniedException("An assigned open shift is required"));
    }
}
