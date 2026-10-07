package vn.edu.huit.smartparking.backend.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "vehicle_right_pending_transitions",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_vehicle_right_pending_transition_relation", columnNames = "vehicle_right_id"),
        indexes = @Index(name = "ix_vehicle_right_pending_transitions_due", columnList = "effective_at,id"))
@Getter
@Setter
public class VehicleRightPendingTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_right_id", nullable = false, foreignKey = @ForeignKey(
            name = "fk_vr_pending_transition_relation"))
    private VehicleResidentRelation vehicleRight;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_actor_user_id", nullable = false, foreignKey = @ForeignKey(
            name = "fk_vr_pending_transition_actor"))
    private User sourceActor;
}
