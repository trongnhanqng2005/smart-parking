package vn.edu.huit.smartparking.backend.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vehicle_categories")
@Getter
@Setter
public class VehicleCategory {
    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "family_id")
    private VehicleFamily family;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}
