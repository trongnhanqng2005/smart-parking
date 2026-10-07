package vn.edu.huit.smartparking.backend.security.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.config.SecurityBootstrapProperties;
import vn.edu.huit.smartparking.backend.security.entity.Permission;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.RolePermissionId;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.entity.UserRoleId;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.PermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RolePermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class SecurityBootstrapService {
    public static final String MANAGEMENT = "MANAGEMENT";
    public static final String GATE_STAFF = "GATE_STAFF";
    public static final String CHANGE_OWN_PASSWORD = "SECURITY_CHANGE_OWN_PASSWORD";
    private static final List<PermissionDefinition> MANAGEMENT_PERMISSIONS = List.of(
            new PermissionDefinition("APARTMENT_READ", "Read apartments", "APARTMENT", "READ",
                    "Read apartment records"),
            new PermissionDefinition("APARTMENT_MANAGE", "Manage apartments", "APARTMENT", "MANAGE",
                    "Create, correct and change apartment status"),
            new PermissionDefinition("RESIDENT_READ", "Read residents", "RESIDENT", "READ",
                    "Read resident records"),
            new PermissionDefinition("RESIDENT_MANAGE", "Manage residents", "RESIDENT", "MANAGE",
                    "Create, correct and change resident status"),
            new PermissionDefinition("HOUSEHOLD_MEMBERSHIP_READ", "Read household memberships",
                    "HOUSEHOLD_MEMBERSHIP", "READ", "Read household membership records"),
            new PermissionDefinition("HOUSEHOLD_MEMBERSHIP_MANAGE", "Manage household memberships",
                    "HOUSEHOLD_MEMBERSHIP", "MANAGE", "Manage household membership lifecycle"),
            new PermissionDefinition("VEHICLE_RIGHT_READ", "Read vehicle rights", "VEHICLE_RIGHT", "READ",
                    "Read vehicle-use rights"),
            new PermissionDefinition("VEHICLE_RIGHT_MANAGE", "Manage vehicle rights", "VEHICLE_RIGHT", "MANAGE",
                    "Manage vehicle-use rights"));

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityBootstrapProperties properties;
    private final AuditService auditService;

    public SecurityBootstrapService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecurityBootstrapProperties properties,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public void initialize() {
        initialize(true);
    }

    @Transactional
    public void initializeCatalog() {
        initialize(false);
    }

    private void initialize(boolean createInitialManagement) {
        Role management = role(MANAGEMENT, "Ban quản lý");
        Role gateStaff = role(GATE_STAFF, "Nhân viên trạm gác");
        if (roleRepository.findAll().stream().anyMatch(role -> !Set.of(MANAGEMENT, GATE_STAFF).contains(role.getCode()))) {
            throw new IllegalStateException("Only MANAGEMENT and GATE_STAFF roles are supported");
        }
        Permission changePassword = permission();
        grant(management, changePassword);
        grant(gateStaff, changePassword);
        for (PermissionDefinition definition : MANAGEMENT_PERMISSIONS) {
            grant(management, permission(definition));
        }

        if (createInitialManagement && !userRoleRepository.existsByRole_Code(MANAGEMENT)) {
            createInitialManagement(management);
        }
    }

    private Role role(String code, String name) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            role.setDescription(name);
            return roleRepository.save(role);
        });
    }

    private Permission permission() {
        return permissionRepository.findByCode(CHANGE_OWN_PASSWORD).orElseGet(() -> {
            Permission permission = new Permission();
            permission.setCode(CHANGE_OWN_PASSWORD);
            permission.setName("Change own password");
            permission.setResource("SECURITY");
            permission.setAction("CHANGE_OWN_PASSWORD");
            permission.setDescription("Change the authenticated user's own password");
            return permissionRepository.save(permission);
        });
    }

    private Permission permission(PermissionDefinition definition) {
        return permissionRepository.findByCode(definition.code()).orElseGet(() -> {
            Permission permission = new Permission();
            permission.setCode(definition.code());
            permission.setName(definition.name());
            permission.setResource(definition.resource());
            permission.setAction(definition.action());
            permission.setDescription(definition.description());
            return permissionRepository.save(permission);
        });
    }

    private void grant(Role role, Permission permission) {
        RolePermissionId id = new RolePermissionId();
        id.setRoleId(role.getId());
        id.setPermissionId(permission.getId());
        if (rolePermissionRepository.existsById(id)) {
            return;
        }
        RolePermission grant = new RolePermission();
        grant.setId(id);
        grant.setRole(role);
        grant.setPermission(permission);
        grant.setAssignedAt(LocalDateTime.now());
        rolePermissionRepository.save(grant);
        auditService.record("RBAC_BOOTSTRAP_PERMISSION_GRANT", "ROLE_PERMISSION",
                role.getId() + ":" + permission.getId(), null, null,
                "{\"role\":\"" + role.getCode() + "\",\"permission\":\"" + permission.getCode() + "\"}");
    }

    private void createInitialManagement(Role management) {
        if (properties.username() == null || properties.password() == null || properties.fullName() == null) {
            throw new IllegalStateException("Initial MANAGEMENT bootstrap credentials are required");
        }
        String username = UsernameCanonicalizer.canonicalize(properties.username());
        PasswordPolicy.validateNewPassword(null, properties.password());

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setFullName(properties.fullName());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        UserRoleId id = new UserRoleId();
        id.setUserId(savedUser.getId());
        id.setRoleId(management.getId());
        UserRole assignment = new UserRole();
        assignment.setId(id);
        assignment.setUser(savedUser);
        assignment.setRole(management);
        assignment.setAssignedAt(LocalDateTime.now());
        userRoleRepository.save(assignment);
        auditService.record("RBAC_BOOTSTRAP_MANAGEMENT", "USER", savedUser.getId().toString(), null,
                null, "{\"role\":\"MANAGEMENT\"}");
    }

    private record PermissionDefinition(String code, String name, String resource, String action, String description) {}
}
