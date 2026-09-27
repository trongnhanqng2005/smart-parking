package vn.edu.huit.smartparking.backend.security.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.entity.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUser_Id(Long userId);

    boolean existsByRole_Code(String roleCode);
}
