package vn.edu.huit.smartparking.backend.security.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions", uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_permissions_code", columnNames = "code"))
@Getter
@Setter
public class Permission {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "resource", length = 100)
    private String resource;

    @Column(name = "action", length = 50)
    private String action;

    @Column(name = "description", length = 255)
    private String description;
}
