package vn.edu.huit.smartparking.backend.vehicle.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleRightPendingTransition;

public interface VehicleRightPendingTransitionRepository
        extends JpaRepository<VehicleRightPendingTransition, Long> {

    @Query("select transition.id from VehicleRightPendingTransition transition "
            + "where transition.effectiveAt <= :dueAt order by transition.effectiveAt, transition.id")
    List<Long> findDueIds(@Param("dueAt") LocalDateTime dueAt, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transition from VehicleRightPendingTransition transition where transition.id = :id")
    Optional<VehicleRightPendingTransition> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transition from VehicleRightPendingTransition transition "
            + "where transition.vehicleRight.id = :vehicleRightId")
    Optional<VehicleRightPendingTransition> findByVehicleRight_IdForUpdate(
            @Param("vehicleRightId") Long vehicleRightId);
}
