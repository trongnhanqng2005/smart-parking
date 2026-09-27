package vn.edu.huit.smartparking.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.entity.UserRoleId;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationSchemaIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void migrationAddsGeneratedIdsAndAuthConstraintsWithoutChangingCompositeJoinKeys() {
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3') AND success = 1",
                Integer.class));
        assertEquals(36, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND column_name = 'id' "
                        + "AND extra LIKE '%auto_increment%'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                        + "AND index_name = 'uk_users_username' AND non_unique = 0",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'user_roles' "
                        + "AND index_name = 'uk_user_roles_user_id' AND non_unique = 0",
                Integer.class));
        assertEquals(6, jdbcTemplate.queryForObject(
                "SELECT datetime_precision FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                        + "AND column_name = 'credential_changed_at'",
                Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name IN ('user_roles', 'role_permissions') "
                        + "AND constraint_name = 'PRIMARY' AND ordinal_position = 2",
                Integer.class));
    }

    @Test
    void bootstrapContainsOnlyTheTwoSupportedRolesAndSelfPasswordPermission() {
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code IN ('MANAGEMENT', 'GATE_STAFF')", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permissions WHERE code = 'SECURITY_CHANGE_OWN_PASSWORD'", Integer.class));
    }

    @Test
    @Transactional
    void identityGeneratesUserIdAndCanonicalUsernameUniquenessRejectsCollisions() {
        User first = createUser("  Auth.Test.Collision ");
        userRepository.saveAndFlush(first);
        assertNotNull(first.getId());
        assertEquals("auth.test.collision", first.getUsername());

        User duplicate = createUser("AUTH.TEST.COLLISION");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    @Transactional
    void userRoleConstraintRejectsSecondBusinessRoleForSameUser() {
        User user = createUser("single.role.test");
        userRepository.saveAndFlush(user);
        Role gateStaff = roleRepository.findByCode("GATE_STAFF").orElseThrow();
        Role management = roleRepository.findByCode("MANAGEMENT").orElseThrow();
        userRoleRepository.saveAndFlush(assignment(user, gateStaff));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRoleRepository.saveAndFlush(assignment(user, management)));
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Schema Test");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserRole assignment(User user, Role role) {
        UserRoleId id = new UserRoleId();
        id.setUserId(user.getId());
        id.setRoleId(role.getId());
        UserRole assignment = new UserRole();
        assignment.setId(id);
        assignment.setUser(user);
        assignment.setRole(role);
        return assignment;
    }
}
