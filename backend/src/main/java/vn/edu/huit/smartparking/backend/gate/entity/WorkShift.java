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
import vn.edu.huit.smartparking.backend.gate.enums.ShiftStatus;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "work_shifts")
@Getter
@Setter
public class WorkShift {
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "shift_code", length = 50)
    private String shiftCode;

    @ManyToOne
    @JoinColumn(name = "lane_id")
    private GateLane lane;

    @ManyToOne
    @JoinColumn(name = "staff_user_id")
    private User staffUser;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "opening_cash", precision = 14, scale = 2)
    private BigDecimal openingCash;

    @Column(name = "closing_cash", precision = 14, scale = 2)
    private BigDecimal closingCash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private ShiftStatus status;

    @ManyToOne
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedByUser;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "note", length = 500)
    private String note;
}
