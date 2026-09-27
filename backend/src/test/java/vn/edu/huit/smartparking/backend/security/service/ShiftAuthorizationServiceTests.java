package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.huit.smartparking.backend.gate.entity.WorkShift;
import vn.edu.huit.smartparking.backend.gate.enums.ShiftStatus;
import vn.edu.huit.smartparking.backend.gate.repository.WorkShiftRepository;

class ShiftAuthorizationServiceTests {
    @Test
    void deniesShiftNotAssignedToTheAuthenticatedUserAndLane() {
        WorkShiftRepository repository = mock(WorkShiftRepository.class);
        when(repository.findByIdAndStaffUser_IdAndLane_IdAndStatus(12L, 8L, 4L, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        ShiftAuthorizationService service = new ShiftAuthorizationService(repository);

        assertThrows(AccessDeniedException.class, () -> service.requireAssignedOpenShift(8L, 12L, 4L));
        verify(repository).findByIdAndStaffUser_IdAndLane_IdAndStatus(12L, 8L, 4L, ShiftStatus.OPEN);
    }

    @Test
    void managementMustAlsoBeAssignedToTheOpenShiftAndLane() {
        WorkShiftRepository repository = mock(WorkShiftRepository.class);
        WorkShift assignedOpenShift = new WorkShift();
        when(repository.findByIdAndStaffUser_IdAndLane_IdAndStatus(15L, 99L, 6L, ShiftStatus.OPEN))
                .thenReturn(Optional.of(assignedOpenShift));
        ShiftAuthorizationService service = new ShiftAuthorizationService(repository);

        assertSame(assignedOpenShift, service.requireAssignedOpenShift(99L, 15L, 6L));
        verify(repository).findByIdAndStaffUser_IdAndLane_IdAndStatus(15L, 99L, 6L, ShiftStatus.OPEN);
    }
}
