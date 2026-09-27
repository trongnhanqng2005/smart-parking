package vn.edu.huit.smartparking.backend.gate.entity;

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
import vn.edu.huit.smartparking.backend.common.persistence.enums.PricingStatus;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "decision_policy_versions")
@Getter
@Setter
public class DecisionPolicyVersion {
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "policy_code", length = 50)
    private String policyCode;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "plate_min_confidence", precision = 5, scale = 4)
    private BigDecimal plateMinConfidence;

    @Column(name = "vehicle_min_confidence", precision = 5, scale = 4)
    private BigDecimal vehicleMinConfidence;

    @Column(name = "face_min_similarity", precision = 5, scale = 4)
    private BigDecimal faceMinSimilarity;

    @Column(name = "liveness_min_score", precision = 5, scale = 4)
    private BigDecimal livenessMinScore;

    @Column(name = "image_quality_min_score", precision = 5, scale = 4)
    private BigDecimal imageQualityMinScore;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private PricingStatus status;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
