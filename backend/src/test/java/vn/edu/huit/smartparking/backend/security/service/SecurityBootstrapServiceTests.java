package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.security.config.SecurityBootstrapProperties;
import vn.edu.huit.smartparking.backend.security.entity.Permission;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.RolePermissionId;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.repository.PermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RolePermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;

class SecurityBootstrapServiceTests {
    @Test
    void bootstrapsManagementPermissionCatalogAndSharedPasswordPermission() {
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        AtomicLong ids = new AtomicLong(1);
        when(roles.findByCode(any())).thenReturn(Optional.empty());
        when(roles.save(any(Role.class))).thenAnswer(call -> {
            Role role = call.getArgument(0);
            role.setId(ids.getAndIncrement());
            return role;
        });
        when(roles.findAll()).thenReturn(List.of());
        when(permissions.findByCode(SecurityBootstrapService.CHANGE_OWN_PASSWORD)).thenReturn(Optional.empty());
        when(permissions.save(any(Permission.class))).thenAnswer(call -> {
            Permission permission = call.getArgument(0);
            permission.setId(ids.getAndIncrement());
            return permission;
        });
        when(grants.existsById(any(RolePermissionId.class))).thenReturn(false);
        when(assignments.existsByRole_Code(SecurityBootstrapService.MANAGEMENT)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(ids.getAndIncrement());
            return user;
        });
        when(encoder.encode("InitialPassword1")).thenReturn("bcrypt-hash");
        SecurityBootstrapService bootstrap = new SecurityBootstrapService(
                roles, permissions, grants, assignments, users, encoder,
                new SecurityBootstrapProperties(" Manager.One ", "InitialPassword1", "Initial Manager"), audit);

        bootstrap.initialize();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        org.mockito.Mockito.verify(roles, org.mockito.Mockito.times(2)).save(roleCaptor.capture());
        assertEquals(List.of("MANAGEMENT", "GATE_STAFF"),
                roleCaptor.getAllValues().stream().map(Role::getCode).toList());
        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(permissions, org.mockito.Mockito.times(9)).save(permissionCaptor.capture());
        assertEquals(Set.of(
                "SECURITY_CHANGE_OWN_PASSWORD",
                "APARTMENT_READ", "APARTMENT_MANAGE",
                "RESIDENT_READ", "RESIDENT_MANAGE",
                "HOUSEHOLD_MEMBERSHIP_READ", "HOUSEHOLD_MEMBERSHIP_MANAGE",
                "VEHICLE_RIGHT_READ", "VEHICLE_RIGHT_MANAGE"),
                permissionCaptor.getAllValues().stream()
                        .map(Permission::getCode).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Map.of(
                        "APARTMENT_READ", "APARTMENT:READ",
                        "APARTMENT_MANAGE", "APARTMENT:MANAGE",
                        "RESIDENT_READ", "RESIDENT:READ",
                        "RESIDENT_MANAGE", "RESIDENT:MANAGE",
                        "HOUSEHOLD_MEMBERSHIP_READ", "HOUSEHOLD_MEMBERSHIP:READ",
                        "HOUSEHOLD_MEMBERSHIP_MANAGE", "HOUSEHOLD_MEMBERSHIP:MANAGE",
                        "VEHICLE_RIGHT_READ", "VEHICLE_RIGHT:READ",
                        "VEHICLE_RIGHT_MANAGE", "VEHICLE_RIGHT:MANAGE"),
                permissionCaptor.getAllValues().stream()
                        .filter(permission -> !SecurityBootstrapService.CHANGE_OWN_PASSWORD.equals(permission.getCode()))
                        .collect(java.util.stream.Collectors.toMap(
                                Permission::getCode, permission -> permission.getResource() + ":" + permission.getAction())));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        assertEquals("manager.one", userCaptor.getValue().getUsername());
        assertEquals("bcrypt-hash", userCaptor.getValue().getPasswordHash());
        ArgumentCaptor<RolePermission> grantCaptor = ArgumentCaptor.forClass(RolePermission.class);
        verify(grants, org.mockito.Mockito.times(10)).save(grantCaptor.capture());
        assertEquals(Set.of(
                "SECURITY_CHANGE_OWN_PASSWORD",
                "APARTMENT_READ", "APARTMENT_MANAGE",
                "RESIDENT_READ", "RESIDENT_MANAGE",
                "HOUSEHOLD_MEMBERSHIP_READ", "HOUSEHOLD_MEMBERSHIP_MANAGE",
                "VEHICLE_RIGHT_READ", "VEHICLE_RIGHT_MANAGE"),
                grantCaptor.getAllValues().stream()
                        .filter(grant -> SecurityBootstrapService.MANAGEMENT.equals(grant.getRole().getCode()))
                        .map(grant -> grant.getPermission().getCode()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(SecurityBootstrapService.CHANGE_OWN_PASSWORD), grantCaptor.getAllValues().stream()
                .filter(grant -> SecurityBootstrapService.GATE_STAFF.equals(grant.getRole().getCode()))
                .map(grant -> grant.getPermission().getCode()).collect(java.util.stream.Collectors.toSet()));
        verify(audit).record("RBAC_BOOTSTRAP_MANAGEMENT", "USER", "12", null, null,
                "{\"role\":\"MANAGEMENT\"}");
        verify(audit).record("RBAC_BOOTSTRAP_PERMISSION_GRANT", "ROLE_PERMISSION", "1:3", null, null,
                "{\"role\":\"MANAGEMENT\",\"permission\":\"SECURITY_CHANGE_OWN_PASSWORD\"}");
    }

    @Test
    void initializesRbacCatalogWithoutCreatingManagementAccount() {
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        AtomicLong ids = new AtomicLong(1);
        when(roles.findByCode(any())).thenReturn(Optional.empty());
        when(roles.save(any(Role.class))).thenAnswer(call -> {
            Role role = call.getArgument(0);
            role.setId(ids.getAndIncrement());
            return role;
        });
        when(roles.findAll()).thenReturn(List.of());
        when(permissions.findByCode(SecurityBootstrapService.CHANGE_OWN_PASSWORD)).thenReturn(Optional.empty());
        when(permissions.save(any(Permission.class))).thenAnswer(call -> {
            Permission permission = call.getArgument(0);
            permission.setId(ids.getAndIncrement());
            return permission;
        });
        when(grants.existsById(any(RolePermissionId.class))).thenReturn(false);
        SecurityBootstrapService bootstrap = new SecurityBootstrapService(
                roles, permissions, grants, assignments, users, mock(PasswordEncoder.class),
                new SecurityBootstrapProperties("test.management", "TestOnly-Management-Password-1", "Test Management"),
                mock(AuditService.class));

        bootstrap.initializeCatalog();

        verify(roles, org.mockito.Mockito.times(2)).save(any(Role.class));
        verify(grants, org.mockito.Mockito.times(10)).save(any(RolePermission.class));
        verify(assignments, never()).save(any(UserRole.class));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void repeatedBootstrapDoesNotReinsertRolesGrantsOrResetManagementCredentials() {
        Role management = role(1L, SecurityBootstrapService.MANAGEMENT);
        Role gateStaff = role(2L, SecurityBootstrapService.GATE_STAFF);
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(roles.findByCode(SecurityBootstrapService.MANAGEMENT)).thenReturn(Optional.of(management));
        when(roles.findByCode(SecurityBootstrapService.GATE_STAFF)).thenReturn(Optional.of(gateStaff));
        when(roles.findAll()).thenReturn(List.of(management, gateStaff));
        when(permissions.findByCode(any())).thenAnswer(call -> {
            Permission permission = new Permission();
            permission.setId(3L);
            permission.setCode(call.getArgument(0));
            return Optional.of(permission);
        });
        when(grants.existsById(any(RolePermissionId.class))).thenReturn(true);
        when(assignments.existsByRole_Code(SecurityBootstrapService.MANAGEMENT)).thenReturn(true);
        SecurityBootstrapService bootstrap = new SecurityBootstrapService(
                roles, permissions, grants, assignments, users, mock(PasswordEncoder.class),
                new SecurityBootstrapProperties(null, null, null), mock(AuditService.class));

        bootstrap.initialize();
        bootstrap.initialize();

        verify(roles, never()).save(any(Role.class));
        verify(permissions, never()).save(any(Permission.class));
        verify(grants, never()).save(any(RolePermission.class));
        verify(assignments, never()).save(any(UserRole.class));
        verify(users, never()).save(any(User.class));
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
