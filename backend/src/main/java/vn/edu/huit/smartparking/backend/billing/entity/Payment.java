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
import vn.edu.huit.smartparking.backend.billing.enums.PaymentMethod;
import vn.edu.huit.smartparking.backend.billing.enums.PaymentStatus;
import vn.edu.huit.smartparking.backend.billing.enums.PaymentType;
import vn.edu.huit.smartparking.backend.gate.entity.WorkShift;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "charge_id")
    private Charge charge;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Column(name = "receipt_no", length = 50)
    private String receiptNo;

    @ManyToOne
    @JoinColumn(name = "original_payment_id")
    private Payment originalPayment;

    @ManyToOne
    @JoinColumn(name = "work_shift_id")
    private WorkShift workShift;

    @ManyToOne
    @JoinColumn(name = "processed_by_user_id")
    private User processedByUser;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
