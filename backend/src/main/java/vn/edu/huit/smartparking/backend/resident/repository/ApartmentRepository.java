package vn.edu.huit.smartparking.backend.resident.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    @Transactional(readOnly = true)
    Optional<Apartment> findByBuildingKeyAndApartmentCodeKey(byte[] buildingKey, byte[] apartmentCodeKey);

    Page<Apartment> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Apartment> findAllByBuildingKeyOrderByCreatedAtDescIdDesc(byte[] buildingKey, Pageable pageable);

    Page<Apartment> findAllByApartmentCodeKeyOrderByCreatedAtDescIdDesc(byte[] apartmentCodeKey, Pageable pageable);

    Page<Apartment> findAllByBuildingKeyAndApartmentCodeKeyOrderByCreatedAtDescIdDesc(
            byte[] buildingKey, byte[] apartmentCodeKey, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select apartment from Apartment apartment where apartment.id = :id")
    Optional<Apartment> findByIdForUpdate(@Param("id") Long id);

    default Optional<Apartment> findByCanonicalBusinessIdentity(String building, String apartmentCode) {
        return findByBuildingKeyAndApartmentCodeKey(
                ResidentIdentityKeyNormalizer.buildingKey(building),
                ResidentIdentityKeyNormalizer.apartmentCodeKey(apartmentCode));
    }
}
