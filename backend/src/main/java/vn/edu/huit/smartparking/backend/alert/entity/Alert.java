package vn.edu.huit.smartparking.backend.alert.entity;

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
import vn.edu.huit.smartparking.backend.alert.enums.AlertSeverity;
import vn.edu.huit.smartparking.backend.alert.enums.AlertStatus;
import vn.edu.huit.smartparking.backend.gate.entity.GateEvent;
import vn.edu.huit.smartparking.backend.parking.entity.ParkingSession;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "alerts")
@Getter
@Setter
public class Alert {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gate_event_id")
    private GateEvent gateEvent;

    @ManyToOne
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @Column(name = "alert_code", length = 100)
    private String alertCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "severity")
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private AlertStatus status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "acknowledged_by_user_id")
    private User acknowledgedByUser;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedByUser;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
