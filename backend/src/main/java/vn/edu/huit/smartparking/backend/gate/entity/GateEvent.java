package vn.edu.huit.smartparking.backend.gate.entity;

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
import vn.edu.huit.smartparking.backend.card.entity.Card;
import vn.edu.huit.smartparking.backend.gate.enums.DecisionOutcome;
import vn.edu.huit.smartparking.backend.gate.enums.DecisionType;
import vn.edu.huit.smartparking.backend.gate.enums.GateEventType;
import vn.edu.huit.smartparking.backend.gate.enums.OperatingMode;
import vn.edu.huit.smartparking.backend.gate.enums.ResolutionSource;
import vn.edu.huit.smartparking.backend.gate.enums.SyncStatus;
import vn.edu.huit.smartparking.backend.parking.entity.ParkingSession;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;

@Entity
@Table(name = "gate_events")
@Getter
@Setter
public class GateEvent {
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "origin_event_id", columnDefinition = "char(36)")
    private String originEventId;

    @ManyToOne
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @ManyToOne
    @JoinColumn(name = "lane_id")
    private GateLane lane;

    @ManyToOne
    @JoinColumn(name = "work_shift_id")
    private WorkShift workShift;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "event_type")
    private GateEventType eventType;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    @ManyToOne
    @JoinColumn(name = "driver_resident_id")
    private Resident driverResident;

    @ManyToOne
    @JoinColumn(name = "resolved_vehicle_id")
    private Vehicle resolvedVehicle;

    @ManyToOne
    @JoinColumn(name = "resolved_vehicle_category_id")
    private VehicleCategory resolvedVehicleCategory;

    @Column(name = "resolved_plate", length = 30)
    private String resolvedPlate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "plate_resolution_source")
    private ResolutionSource plateResolutionSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "vehicle_resolution_source")
    private ResolutionSource vehicleResolutionSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "driver_resolution_source")
    private ResolutionSource driverResolutionSource;

    @ManyToOne
    @JoinColumn(name = "decision_policy_version_id")
    private DecisionPolicyVersion decisionPolicyVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "decision")
    private DecisionType decision;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "final_outcome")
    private DecisionOutcome finalOutcome;

    @Column(name = "decision_reason_code", length = 100)
    private String decisionReasonCode;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "operating_mode")
    private OperatingMode operatingMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "sync_status")
    private SyncStatus syncStatus;

    @Column(name = "sync_conflict_reason", length = 500)
    private String syncConflictReason;

    @ManyToOne
    @JoinColumn(name = "operator_user_id")
    private User operatorUser;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
