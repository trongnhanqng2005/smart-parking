package vn.edu.huit.smartparking.backend.resident.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;

public interface ApartmentMembershipRepository extends JpaRepository<ApartmentMembership, Long> {
    Page<ApartmentMembership> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<ApartmentMembership> findAllByApartment_IdOrderByCreatedAtDescIdDesc(Long apartmentId, Pageable pageable);

    Page<ApartmentMembership> findAllByResident_IdOrderByCreatedAtDescIdDesc(Long residentId, Pageable pageable);

    Page<ApartmentMembership> findAllByApartment_IdAndResident_IdOrderByCreatedAtDescIdDesc(
            Long apartmentId, Long residentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ApartmentMembership membership where membership.id = :id")
    Optional<ApartmentMembership> findByIdForUpdate(@Param("id") Long id);

    @Query("select membership from ApartmentMembership membership "
            + "where membership.apartment.id = :apartmentId and membership.status = :active "
            + "and (:validTo is null or membership.validFrom < :validTo) "
            + "and (membership.validTo is null or membership.validTo > :validFrom)")
    List<ApartmentMembership> findActiveOverlaps(
            @Param("apartmentId") Long apartmentId,
            @Param("validFrom") LocalDateTime validFrom,
            @Param("validTo") LocalDateTime validTo,
            @Param("active") MembershipStatus active);

    @Query("select membership from ApartmentMembership membership "
            + "where membership.apartment.id = :apartmentId and membership.status = :active "
            + "and membership.memberRole = :memberRole "
            + "and (:validTo is null or membership.validFrom < :validTo) "
            + "and (membership.validTo is null or membership.validTo > :validFrom)")
    List<ApartmentMembership> findActiveRoleOverlaps(
            @Param("apartmentId") Long apartmentId,
            @Param("memberRole") MembershipRole memberRole,
            @Param("validFrom") LocalDateTime validFrom,
            @Param("validTo") LocalDateTime validTo,
            @Param("active") MembershipStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ApartmentMembership membership "
            + "where membership.apartment.id = :apartmentId and membership.resident.id = :residentId "
            + "and membership.status = :active and membership.validFrom <= :at "
            + "and (membership.validTo is null or membership.validTo > :at) order by membership.id")
    List<ApartmentMembership> findEffectiveForUpdate(
            @Param("apartmentId") Long apartmentId,
            @Param("residentId") Long residentId,
            @Param("at") LocalDateTime at,
            @Param("active") MembershipStatus active);

    @Query("select membership from ApartmentMembership membership where membership.resident.id = :residentId "
            + "and membership.status = :active order by membership.id")
    List<ApartmentMembership> findActiveForResident(
            @Param("residentId") Long residentId,
            @Param("active") MembershipStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ApartmentMembership membership where membership.resident.id = :residentId "
            + "and membership.status = :active order by membership.id")
    List<ApartmentMembership> findActiveForResidentForUpdate(
            @Param("residentId") Long residentId,
            @Param("active") MembershipStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ApartmentMembership membership "
            + "where membership.apartment.id = :apartmentId and membership.resident.id = :residentId "
            + "order by membership.id")
    List<ApartmentMembership> findAllForApartmentAndResidentForUpdate(
            @Param("apartmentId") Long apartmentId,
            @Param("residentId") Long residentId);

}
