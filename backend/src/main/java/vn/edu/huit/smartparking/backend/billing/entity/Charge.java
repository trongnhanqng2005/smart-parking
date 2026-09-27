package vn.edu.huit.smartparking.backend.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.huit.smartparking.backend.billing.enums.ChargeStatus;
import vn.edu.huit.smartparking.backend.parking.entity.ParkingSession;
import vn.edu.huit.smartparking.backend.pricing.entity.PricingVersion;
import vn.edu.huit.smartparking.backend.subscription.entity.ParkingSubscription;

@Entity
@Table(name = "charges")
@Getter
@Setter
public class Charge {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "invoice_no", length = 50)
    private String invoiceNo;

    @ManyToOne
    @JoinColumn(name = "parking_subscription_id")
    private ParkingSubscription parkingSubscription;

    @ManyToOne
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @ManyToOne
    @JoinColumn(name = "pricing_version_id")
    private PricingVersion pricingVersion;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private ChargeStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
