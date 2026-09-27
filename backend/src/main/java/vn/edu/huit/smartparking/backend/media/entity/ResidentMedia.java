package vn.edu.huit.smartparking.backend.media.entity;

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
import vn.edu.huit.smartparking.backend.media.enums.ResidentMediaPurpose;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;

@Entity
@Table(name = "resident_media")
@Getter
@Setter
public class ResidentMedia {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private MediaAsset media;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "purpose")
    private ResidentMediaPurpose purpose;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
