package vn.edu.huit.smartparking.backend.security.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.repository.RolePermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;

@Service
public class SecurityAccountService {
    private static final Set<String> BUSINESS_ROLES = Set.of("MANAGEMENT", "GATE_STAFF");
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    public SecurityAccountService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
    }

    public AuthenticatedAccount loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return load(user);
    }

    public AuthenticatedAccount load(User user) {
        LinkedHashSet<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        List<UserRole> assignedRoles = userRoleRepository.findAllByUser_Id(user.getId());
        if (assignedRoles.size() != 1 || !BUSINESS_ROLES.contains(assignedRoles.getFirst().getRole().getCode())) {
            throw new UsernameNotFoundException("Account is not assigned one supported business role");
        }
        var assignedRole = assignedRoles.getFirst().getRole();
        addRoleAuthorities(assignedRole, authorities);
        if ("MANAGEMENT".equals(assignedRole.getCode())) {
            var gateStaffRole = roleRepository.findByCode("GATE_STAFF")
                    .orElseThrow(() -> new UsernameNotFoundException("Supported roles are not initialized"));
            addRoleAuthorities(gateStaffRole, authorities);
        }
        return new AuthenticatedAccount(
                user.getId(), user.getUsername(), user.getStatus(),
                user.getCredentialChangedAt(), List.copyOf(authorities));
    }

    private void addRoleAuthorities(Role role, Set<SimpleGrantedAuthority> authorities) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
        for (RolePermission rolePermission : rolePermissionRepository.findAllByRole_Id(role.getId())) {
            authorities.add(new SimpleGrantedAuthority(rolePermission.getPermission().getCode()));
        }
    }
}
