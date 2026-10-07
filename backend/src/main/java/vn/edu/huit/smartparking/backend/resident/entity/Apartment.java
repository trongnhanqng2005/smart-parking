package vn.edu.huit.smartparking.backend.resident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;
import vn.edu.huit.smartparking.backend.resident.enums.ApartmentStatus;

@Entity
@Table(name = "apartments", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_apartments_normalized_identity", columnNames = {"building_key", "apartment_code_key"}))
@Getter
@Setter
public class Apartment {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "apartment_code", length = 50, nullable = false)
    private String apartmentCode;

    @Column(name = "apartment_code_key", length = 768, nullable = false, columnDefinition = "VARBINARY(768)")
    @Setter(AccessLevel.NONE)
    private byte[] apartmentCodeKey;

    @Column(name = "building", length = 100, nullable = false)
    private String building;

    @Column(name = "building_key", length = 2048, nullable = false, columnDefinition = "VARBINARY(2048)")
    @Setter(AccessLevel.NONE)
    private byte[] buildingKey;

    @Column(name = "floor_no")
    private Integer floorNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "status")
    private ApartmentStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void setApartmentCode(String apartmentCode) {
        byte[] key = ResidentIdentityKeyNormalizer.apartmentCodeKey(apartmentCode);
        this.apartmentCode = apartmentCode;
        this.apartmentCodeKey = key;
    }

    public void setBuilding(String building) {
        byte[] key = ResidentIdentityKeyNormalizer.buildingKey(building);
        this.building = building;
        this.buildingKey = key;
    }
}
