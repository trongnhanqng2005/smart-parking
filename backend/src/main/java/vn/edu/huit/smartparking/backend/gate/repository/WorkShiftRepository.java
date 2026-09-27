package vn.edu.huit.smartparking.backend.gate.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.huit.smartparking.backend.gate.entity.WorkShift;
import vn.edu.huit.smartparking.backend.gate.enums.ShiftStatus;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {
    Optional<WorkShift> findByIdAndStaffUser_IdAndLane_IdAndStatus(
            Long id, Long userId, Long laneId, ShiftStatus status);
}
