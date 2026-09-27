package vn.edu.huit.smartparking.backend.security.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_roles", uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_user_roles_user_id", columnNames = "user_id"))
@Getter
@Setter
public class UserRole {
    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("roleId")
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @jakarta.persistence.Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
