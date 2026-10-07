package vn.edu.huit.smartparking.backend.vehicle.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Page<Vehicle> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Vehicle> findAllByPlateNormalizedOrderByCreatedAtDescIdDesc(String plateNormalized, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vehicle from Vehicle vehicle where vehicle.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
}
