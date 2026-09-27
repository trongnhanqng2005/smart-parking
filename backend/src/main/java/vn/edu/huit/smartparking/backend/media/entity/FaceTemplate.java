package vn.edu.huit.smartparking.backend.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;

@Entity
@Table(name = "face_templates")
@Getter
@Setter
public class FaceTemplate {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @ManyToOne
    @JoinColumn(name = "source_media_id")
    private MediaAsset sourceMedia;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "template_storage_ref", length = 500)
    private String templateStorageRef;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
