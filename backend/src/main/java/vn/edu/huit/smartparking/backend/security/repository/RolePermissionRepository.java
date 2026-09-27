package vn.edu.huit.smartparking.backend.security.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    @EntityGraph(attributePaths = "permission")
    List<RolePermission> findAllByRole_Id(Long roleId);
}
