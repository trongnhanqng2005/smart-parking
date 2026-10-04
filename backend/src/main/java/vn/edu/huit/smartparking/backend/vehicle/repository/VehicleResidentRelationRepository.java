package vn.edu.huit.smartparking.backend.vehicle.repository;

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
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;

public interface VehicleResidentRelationRepository extends JpaRepository<VehicleResidentRelation, Long> {
    List<VehicleResidentRelation> findAllByResident_IdOrderById(Long residentId);

    List<VehicleResidentRelation> findAllByVehicle_IdOrderById(Long vehicleId);

    @Query("select distinct relation.resident.id from VehicleResidentRelation relation "
            + "where relation.vehicle.id = :vehicleId and relation.status = :active "
            + "and (:validTo is null or relation.validFrom < :validTo) "
            + "and (relation.validTo is null or relation.validTo > :validFrom)")
    @Transactional(readOnly = true)
    List<Long> findActiveOverlapResidentIds(
            @Param("vehicleId") Long vehicleId,
            @Param("validFrom") LocalDateTime validFrom,
            @Param("validTo") LocalDateTime validTo,
            @Param("active") VehicleRelationStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from VehicleResidentRelation relation where relation.id = :id")
    Optional<VehicleResidentRelation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from VehicleResidentRelation relation "
            + "where relation.vehicle.id = :vehicleId and relation.status = :active "
            + "and (:validTo is null or relation.validFrom < :validTo) "
            + "and (relation.validTo is null or relation.validTo > :validFrom) "
            + "order by relation.id")
    List<VehicleResidentRelation> findActiveOverlapsForUpdate(
            @Param("vehicleId") Long vehicleId,
            @Param("validFrom") LocalDateTime validFrom,
            @Param("validTo") LocalDateTime validTo,
            @Param("active") VehicleRelationStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from VehicleResidentRelation relation where relation.vehicle.id = :vehicleId "
            + "and relation.status = :active order by relation.id")
    List<VehicleResidentRelation> findAllActiveForVehicleForUpdate(
            @Param("vehicleId") Long vehicleId,
            @Param("active") VehicleRelationStatus active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from VehicleResidentRelation relation where relation.vehicle.id = :vehicleId "
            + "order by relation.id")
    List<VehicleResidentRelation> findAllForVehicleForUpdate(@Param("vehicleId") Long vehicleId);

    @Query("select distinct relation.guarantorApartment.id from VehicleResidentRelation relation "
            + "where relation.vehicle.id = :vehicleId and relation.relationType = :authorizedUser "
            + "and relation.status = :active and relation.guarantorType = :householdHead "
            + "and (relation.validTo is null or relation.validTo > :lossAt) "
            + "and exists (select membership.id from ApartmentMembership membership "
            + "where membership.apartment.id = relation.guarantorApartment.id "
            + "and membership.resident.id = :ownerResidentId and membership.status = :activeMembership "
            + "and membership.validFrom <= :lossAt "
            + "and (membership.validTo is null or membership.validTo > :lossAt))")
    List<Long> findHouseholdGuarantorApartmentsForOwnerLoss(
            @Param("vehicleId") Long vehicleId,
            @Param("ownerResidentId") Long ownerResidentId,
            @Param("lossAt") LocalDateTime lossAt,
            @Param("authorizedUser") VehicleRelationType authorizedUser,
            @Param("householdHead") VehicleRelationGuarantorType householdHead,
            @Param("active") VehicleRelationStatus active,
            @Param("activeMembership") MembershipStatus activeMembership);

    @Query("select distinct grantRelation.vehicle.id from VehicleResidentRelation grantRelation "
            + "where grantRelation.relationType = :authorizedUser and grantRelation.status = :active "
            + "and grantRelation.guarantorType = :householdHead "
            + "and grantRelation.guarantorApartment.id = :apartmentId "
            + "and (grantRelation.validTo is null or grantRelation.validTo > :lossAt) "
            + "and (grantRelation.guarantorResident.id = :residentId or exists "
            + "(select ownerRelation.id from VehicleResidentRelation ownerRelation "
            + "where ownerRelation.vehicle.id = grantRelation.vehicle.id "
            + "and ownerRelation.relationType = :owner and ownerRelation.status = :active "
            + "and ownerRelation.resident.id = :residentId and ownerRelation.validFrom <= :lossAt "
            + "and (ownerRelation.validTo is null or ownerRelation.validTo >= :lossAt)))")
    List<Long> findVehiclesAffectedByMembershipLoss(
            @Param("apartmentId") Long apartmentId,
            @Param("residentId") Long residentId,
            @Param("lossAt") LocalDateTime lossAt,
            @Param("authorizedUser") VehicleRelationType authorizedUser,
            @Param("householdHead") VehicleRelationGuarantorType householdHead,
            @Param("owner") VehicleRelationType owner,
            @Param("active") VehicleRelationStatus active);

    @Query("select distinct grantRelation from VehicleResidentRelation grantRelation "
            + "where grantRelation.relationType = :authorizedUser and grantRelation.status = :active "
            + "and (grantRelation.validTo is null or grantRelation.validTo > :statusAt) "
            + "and (grantRelation.guarantorResident.id = :residentId or "
            + "(grantRelation.guarantorType = :householdHead and exists "
            + "(select ownerRelation.id from VehicleResidentRelation ownerRelation "
            + "where ownerRelation.vehicle.id = grantRelation.vehicle.id "
            + "and ownerRelation.relationType = :owner and ownerRelation.status = :active "
            + "and ownerRelation.resident.id = :residentId "
            + "and ownerRelation.validFrom <= grantRelation.validFrom "
            + "and (ownerRelation.validTo is null or ownerRelation.validTo > grantRelation.validFrom) "
            + "and exists (select membership.id from ApartmentMembership membership "
            + "where membership.apartment.id = grantRelation.guarantorApartment.id "
            + "and membership.resident.id = :residentId and membership.status = :activeMembership "
            + "and membership.validFrom <= grantRelation.validFrom "
            + "and (membership.validTo is null or membership.validTo > grantRelation.validFrom)))))")
    List<VehicleResidentRelation> findDependentAuthorizedUsersForResidentStatusLoss(
            @Param("residentId") Long residentId,
            @Param("statusAt") LocalDateTime statusAt,
            @Param("authorizedUser") VehicleRelationType authorizedUser,
            @Param("owner") VehicleRelationType owner,
            @Param("householdHead") VehicleRelationGuarantorType householdHead,
            @Param("active") VehicleRelationStatus active,
            @Param("activeMembership") MembershipStatus activeMembership);

    @Query("select distinct grantRelation.vehicle.id from VehicleResidentRelation grantRelation "
            + "where grantRelation.relationType = :authorizedUser and grantRelation.status <> :voided "
            + "and grantRelation.guarantorType = :householdHead "
            + "and grantRelation.guarantorApartment.id = :apartmentId "
            + "and (((grantRelation.validFrom >= :membershipValidFrom "
            + "and (:membershipValidTo is null or grantRelation.validFrom < :membershipValidTo)) "
            + "and ((:memberIsHead = true and grantRelation.guarantorResident.id = :residentId) or exists "
            + "(select ownerRelation.id from VehicleResidentRelation ownerRelation "
            + "where ownerRelation.vehicle.id = grantRelation.vehicle.id "
            + "and ownerRelation.relationType = :owner and ownerRelation.resident.id = :residentId "
            + "and ownerRelation.validFrom <= grantRelation.validFrom "
            + "and (ownerRelation.validTo is null or ownerRelation.validTo > grantRelation.validFrom)))) "
            + "or (grantRelation.status = :preEffectiveCancelled "
            + "and grantRelation.lifecycleChangedAt >= :membershipValidFrom "
            + "and (:membershipValidTo is null or grantRelation.lifecycleChangedAt <= :membershipValidTo) "
            + "and ((:memberIsHead = true and grantRelation.guarantorResident.id = :residentId) or exists "
            + "(select ownerRelation.id from VehicleResidentRelation ownerRelation "
            + "where ownerRelation.vehicle.id = grantRelation.vehicle.id "
            + "and ownerRelation.relationType = :owner and ownerRelation.resident.id = :residentId "
            + "and ownerRelation.validFrom <= grantRelation.lifecycleChangedAt "
            + "and (ownerRelation.validTo is null "
            + "or ownerRelation.validTo >= grantRelation.lifecycleChangedAt)))))")
    List<Long> findVehiclesAffectedByMembershipVoid(
            @Param("apartmentId") Long apartmentId,
            @Param("residentId") Long residentId,
            @Param("memberIsHead") boolean memberIsHead,
            @Param("membershipValidFrom") LocalDateTime membershipValidFrom,
            @Param("membershipValidTo") LocalDateTime membershipValidTo,
            @Param("authorizedUser") VehicleRelationType authorizedUser,
            @Param("householdHead") VehicleRelationGuarantorType householdHead,
            @Param("owner") VehicleRelationType owner,
            @Param("voided") VehicleRelationStatus voided,
            @Param("preEffectiveCancelled") VehicleRelationStatus preEffectiveCancelled);

    @Query("select relation from VehicleResidentRelation relation "
            + "where (:vehicleId is null or relation.vehicle.id = :vehicleId) "
            + "and (:residentId is null or relation.resident.id = :residentId) "
            + "and (:relationType is null or relation.relationType = :relationType)")
    Page<VehicleResidentRelation> findByFilters(
            @Param("vehicleId") Long vehicleId,
            @Param("residentId") Long residentId,
            @Param("relationType") VehicleRelationType relationType,
            Pageable pageable);
}
