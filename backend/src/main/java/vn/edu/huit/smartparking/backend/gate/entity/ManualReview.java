package vn.edu.huit.smartparking.backend.gate.entity;

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
import vn.edu.huit.smartparking.backend.gate.enums.ManualReviewDecision;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "manual_reviews")
@Getter
@Setter
public class ManualReview {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gate_event_id")
    private GateEvent gateEvent;

    @ManyToOne
    @JoinColumn(name = "reviewer_user_id")
    private User reviewerUser;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "decision")
    private ManualReviewDecision decision;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
