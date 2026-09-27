package vn.edu.huit.smartparking.backend.resident.entity;

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
import vn.edu.huit.smartparking.backend.media.entity.MediaAsset;
import vn.edu.huit.smartparking.backend.resident.enums.VerificationResult;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "identity_verifications")
@Getter
@Setter
public class IdentityVerification {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @ManyToOne
    @JoinColumn(name = "cccd_media_id")
    private MediaAsset cccdMedia;

    @ManyToOne
    @JoinColumn(name = "registration_media_id")
    private MediaAsset registrationMedia;

    @ManyToOne
    @JoinColumn(name = "realtime_media_id")
    private MediaAsset realtimeMedia;

    @Column(name = "cccd_registration_score", precision = 5, scale = 4)
    private BigDecimal cccdRegistrationScore;

    @Column(name = "registration_realtime_score", precision = 5, scale = 4)
    private BigDecimal registrationRealtimeScore;

    @Column(name = "cccd_realtime_score", precision = 5, scale = 4)
    private BigDecimal cccdRealtimeScore;

    @Column(name = "threshold_used", precision = 5, scale = 4)
    private BigDecimal thresholdUsed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "result")
    private VerificationResult result;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @ManyToOne
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedByUser;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
