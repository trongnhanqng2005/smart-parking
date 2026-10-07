package vn.edu.huit.smartparking.backend.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;

@Entity
@Table(name = "vehicle_resident_relations")
@Getter
@Setter
public class VehicleResidentRelation {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "guarantor_type")
    private VehicleRelationGuarantorType guarantorType;

    @ManyToOne
    @JoinColumn(name = "guarantor_resident_id", foreignKey = @jakarta.persistence.ForeignKey(
            name = "fk_vehicle_relation_guarantor_resident"))
    private Resident guarantorResident;

    @ManyToOne
    @JoinColumn(name = "guarantor_apartment_id", foreignKey = @jakarta.persistence.ForeignKey(
            name = "fk_vehicle_relation_guarantor_apartment"))
    private Apartment guarantorApartment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "relation_type")
    private VehicleRelationType relationType;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private VehicleRelationStatus status;

    @Column(name = "lifecycle_changed_at")
    private LocalDateTime lifecycleChangedAt;

    @Column(name = "lifecycle_reason", length = 500)
    private String lifecycleReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
