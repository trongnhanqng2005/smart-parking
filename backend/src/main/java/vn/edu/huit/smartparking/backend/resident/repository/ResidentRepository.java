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
import vn.edu.huit.smartparking.backend.resident.entity.Resident;

public interface ResidentRepository extends JpaRepository<Resident, Long> {
    @Transactional(readOnly = true)
    Optional<Resident> findByIdentityNumberKey(byte[] identityNumberKey);

    Page<Resident> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Resident> findAllByFullNameContainingOrderByCreatedAtDescIdDesc(String fullName, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resident from Resident resident where resident.identityNumberKey = :identityNumberKey")
    Optional<Resident> findByIdentityNumberKeyForUpdate(@Param("identityNumberKey") byte[] identityNumberKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resident from Resident resident where resident.id = :id")
    Optional<Resident> findByIdForUpdate(@Param("id") Long id);

    default Optional<Resident> findByCanonicalIdentityNumber(String identityNumber) {
        return findByIdentityNumberKey(ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber));
    }
}
