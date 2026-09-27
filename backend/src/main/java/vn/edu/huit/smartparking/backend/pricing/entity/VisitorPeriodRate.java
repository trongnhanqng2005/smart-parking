package vn.edu.huit.smartparking.backend.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.huit.smartparking.backend.pricing.enums.PricingPeriodType;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;

@Entity
@Table(name = "visitor_period_rates")
@Getter
@Setter
public class VisitorPeriodRate {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pricing_version_id")
    private PricingVersion pricingVersion;

    @ManyToOne
    @JoinColumn(name = "vehicle_family_id")
    private VehicleFamily vehicleFamily;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "period_type")
    private PricingPeriodType periodType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "cycle_minutes")
    private Integer cycleMinutes;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;
}
