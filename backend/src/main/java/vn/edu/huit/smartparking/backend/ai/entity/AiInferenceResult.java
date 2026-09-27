package vn.edu.huit.smartparking.backend.ai.entity;

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
import vn.edu.huit.smartparking.backend.ai.enums.AiInferenceStatus;
import vn.edu.huit.smartparking.backend.gate.entity.GateEvent;
import vn.edu.huit.smartparking.backend.media.entity.FaceTemplate;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;

@Entity
@Table(name = "ai_inference_results")
@Getter
@Setter
public class AiInferenceResult {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gate_event_id")
    private GateEvent gateEvent;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private AiInferenceStatus status;

    @Column(name = "predicted_plate", length = 30)
    private String predictedPlate;

    @Column(name = "plate_confidence", precision = 5, scale = 4)
    private BigDecimal plateConfidence;

    @ManyToOne
    @JoinColumn(name = "predicted_vehicle_family_id")
    private VehicleFamily predictedVehicleFamily;

    @Column(name = "vehicle_confidence", precision = 5, scale = 4)
    private BigDecimal vehicleConfidence;

    @ManyToOne
    @JoinColumn(name = "matched_face_template_id")
    private FaceTemplate matchedFaceTemplate;

    @Column(name = "face_similarity", precision = 5, scale = 4)
    private BigDecimal faceSimilarity;

    @Column(name = "liveness_score", precision = 5, scale = 4)
    private BigDecimal livenessScore;

    @Column(name = "image_quality_score", precision = 5, scale = 4)
    private BigDecimal imageQualityScore;

    @Column(name = "anpr_model_version", length = 100)
    private String anprModelVersion;

    @Column(name = "ocr_model_version", length = 100)
    private String ocrModelVersion;

    @Column(name = "vehicle_model_version", length = 100)
    private String vehicleModelVersion;

    @Column(name = "face_model_version", length = 100)
    private String faceModelVersion;

    @Column(name = "liveness_model_version", length = 100)
    private String livenessModelVersion;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
