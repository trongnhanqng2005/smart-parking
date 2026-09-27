package vn.edu.huit.smartparking.backend.parking.entity;

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
import vn.edu.huit.smartparking.backend.parking.enums.ParkingCustomerType;
import vn.edu.huit.smartparking.backend.parking.enums.ParkingSessionStatus;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;

@Entity
@Table(name = "parking_sessions")
@Getter
@Setter
public class ParkingSession {
    @Id
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "customer_type")
    private ParkingCustomerType customerType;

    @ManyToOne
    @JoinColumn(name = "registered_vehicle_id")
    private Vehicle registeredVehicle;

    @ManyToOne
    @JoinColumn(name = "access_card_id")
    private Card accessCard;

    @Column(name = "entry_plate", length = 30)
    private String entryPlate;

    @ManyToOne
    @JoinColumn(name = "entry_vehicle_category_id")
    private VehicleCategory entryVehicleCategory;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "exited_at")
    private LocalDateTime exitedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private ParkingSessionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
