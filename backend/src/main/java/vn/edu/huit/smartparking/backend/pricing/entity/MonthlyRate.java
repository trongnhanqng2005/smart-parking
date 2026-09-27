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
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;

@Entity
@Table(name = "monthly_rates")
@Getter
@Setter
public class MonthlyRate {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pricing_version_id")
    private PricingVersion pricingVersion;

    @ManyToOne
    @JoinColumn(name = "vehicle_category_id")
    private VehicleCategory vehicleCategory;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;
}
