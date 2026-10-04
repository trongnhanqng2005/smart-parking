package vn.edu.huit.smartparking.backend.resident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;

@Entity
@Table(name = "residents", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_residents_identity_number_key", columnNames = "identity_number_key"))
@Getter
@Setter
public class Resident {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "full_name", length = 150, nullable = false)
    private String fullName;

    @Column(name = "identity_number", length = 30, nullable = false)
    private String identityNumber;

    @Column(name = "identity_number_key", length = 512, nullable = false, columnDefinition = "VARBINARY(512)")
    @Setter(AccessLevel.NONE)
    private byte[] identityNumberKey;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private ResidentStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void setIdentityNumber(String identityNumber) {
        byte[] key = ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber);
        this.identityNumber = identityNumber;
        this.identityNumberKey = key;
    }
}
