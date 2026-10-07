package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.edu.huit.smartparking.backend.security.entity.Permission;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.RolePermissionRepository;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;

class SecurityAccountServiceTests {
    @Test
    void resolvesCurrentRoleAndPermissionsFromAssignments() {
        UserRepository users = mock(UserRepository.class);
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        SecurityAccountService service = new SecurityAccountService(users, assignments, grants, roles);
        User user = user();
        Role role = new Role();
        role.setId(2L);
        role.setCode("GATE_STAFF");
        UserRole assignment = new UserRole();
        assignment.setRole(role);
        Permission permission = new Permission();
        permission.setCode("SECURITY_CHANGE_OWN_PASSWORD");
        RolePermission grant = new RolePermission();
        grant.setPermission(permission);
        when(assignments.findAllByUser_Id(1L)).thenReturn(List.of(assignment));
        when(grants.findAllByRole_Id(2L)).thenReturn(List.of(grant));

        AuthenticatedAccount account = service.load(user);

        assertEquals(1L, account.userId());
        assertTrue(account.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_GATE_STAFF")));
        assertTrue(account.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SECURITY_CHANGE_OWN_PASSWORD")));
    }

    @Test
    void refusesAccountWithNoBusinessRole() {
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        when(assignments.findAllByUser_Id(1L)).thenReturn(List.of());
        SecurityAccountService service = new SecurityAccountService(
                mock(UserRepository.class), assignments, mock(RolePermissionRepository.class), mock(RoleRepository.class));

        assertThrows(UsernameNotFoundException.class, () -> service.load(user()));
    }

    @Test
    void managementInheritsGateStaffAuthoritiesWithoutChangingItsAssignedRole() {
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        Role management = new Role();
        management.setId(1L);
        management.setCode("MANAGEMENT");
        Role gateStaff = new Role();
        gateStaff.setId(2L);
        gateStaff.setCode("GATE_STAFF");
        UserRole assignment = new UserRole();
        assignment.setRole(management);
        Permission gatePermission = new Permission();
        gatePermission.setCode("GATE_OPERATION");
        RolePermission grant = new RolePermission();
        grant.setPermission(gatePermission);
        when(assignments.findAllByUser_Id(1L)).thenReturn(List.of(assignment));
        when(roles.findByCode("GATE_STAFF")).thenReturn(java.util.Optional.of(gateStaff));
        when(grants.findAllByRole_Id(1L)).thenReturn(List.of());
        when(grants.findAllByRole_Id(2L)).thenReturn(List.of(grant));

        AuthenticatedAccount account = new SecurityAccountService(
                mock(UserRepository.class), assignments, grants, roles).load(user());

        assertTrue(account.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGEMENT")));
        assertTrue(account.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_GATE_STAFF")));
        assertTrue(account.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("GATE_OPERATION")));
    }

    @Test
    void managementReceivesCurrentDatabasePermissionsOnEachLoad() {
        UserRepository users = mock(UserRepository.class);
        UserRoleRepository assignments = mock(UserRoleRepository.class);
        RolePermissionRepository grants = mock(RolePermissionRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        Role management = role(2L, "MANAGEMENT");
        Role gateStaff = role(3L, "GATE_STAFF");
        UserRole assignment = new UserRole();
        assignment.setRole(management);
        when(users.findById(1L)).thenReturn(Optional.of(user()));
        when(assignments.findAllByUser_Id(1L)).thenReturn(List.of(assignment));
        when(roles.findByCode("GATE_STAFF")).thenReturn(Optional.of(gateStaff));
        when(grants.findAllByRole_Id(2L)).thenReturn(
                List.of(grant("APARTMENT_READ")), List.of(grant("APARTMENT_MANAGE")));
        when(grants.findAllByRole_Id(3L)).thenReturn(List.of());
        SecurityAccountService service = new SecurityAccountService(users, assignments, grants, roles);

        AuthenticatedAccount beforeGrantChange = service.loadUserById(1L);
        AuthenticatedAccount afterGrantChange = service.loadUserById(1L);

        assertTrue(hasAuthority(beforeGrantChange, "APARTMENT_READ"));
        assertFalse(hasAuthority(beforeGrantChange, "APARTMENT_MANAGE"));
        assertTrue(hasAuthority(afterGrantChange, "APARTMENT_MANAGE"));
        assertFalse(hasAuthority(afterGrantChange, "APARTMENT_READ"));
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("operator");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    private RolePermission grant(String permissionCode) {
        Permission permission = new Permission();
        permission.setCode(permissionCode);
        RolePermission grant = new RolePermission();
        grant.setPermission(permission);
        return grant;
    }

    private boolean hasAuthority(AuthenticatedAccount account, String authority) {
        return account.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
