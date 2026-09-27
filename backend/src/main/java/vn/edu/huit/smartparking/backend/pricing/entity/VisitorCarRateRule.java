package vn.edu.huit.smartparking.backend.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;

@Entity
@Table(name = "visitor_car_rate_rules")
@Getter
@Setter
public class VisitorCarRateRule {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pricing_version_id")
    private PricingVersion pricingVersion;

    @ManyToOne
    @JoinColumn(name = "vehicle_family_id")
    private VehicleFamily vehicleFamily;

    @Column(name = "base_minutes")
    private Integer baseMinutes;

    @Column(name = "base_fee", precision = 14, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "increment_minutes")
    private Integer incrementMinutes;

    @Column(name = "increment_fee", precision = 14, scale = 2)
    private BigDecimal incrementFee;

    @Column(name = "overnight_min_fee", precision = 14, scale = 2)
    private BigDecimal overnightMinFee;
}
