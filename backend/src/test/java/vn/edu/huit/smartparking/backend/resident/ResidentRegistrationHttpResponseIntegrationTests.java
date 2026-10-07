package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.security.entity.UserRoleId;
import vn.edu.huit.smartparking.backend.security.enums.UserStatus;
import vn.edu.huit.smartparking.backend.security.repository.RoleRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRepository;
import vn.edu.huit.smartparking.backend.security.repository.UserRoleRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResidentRegistrationHttpResponseIntegrationTests {
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>");
    private static final Pattern SESSION_COOKIE = Pattern.compile("^(JSESSIONID=[^;]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpClient httpClient;
    private String sessionCookie;
    private String username;
    private String password;

    @BeforeEach
    void prepareAuthenticatedManagementSession() throws Exception {
        String databaseUrl;
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            databaseUrl = connection.getMetaData().getURL();
        }
        assertTrue(databaseUrl.startsWith("jdbc:mysql://127.0.0.1:3306/smart_parking"),
                "This real-HTTP integration test may only write to the authorized local test database");

        httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        String token = UUID.randomUUID().toString().replace("-", "");
        username = "synthetic.qa.nv01.http." + token;
        password = "TestOnly-" + token + "-Password1!";

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName("SYNTHETIC QA ONLY NV01 HTTP integration test");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.saveAndFlush(user);

        Role management = roleRepository.findByCode("MANAGEMENT").orElseThrow();
        UserRoleId userRoleId = new UserRoleId();
        userRoleId.setUserId(user.getId());
        userRoleId.setRoleId(management.getId());
        UserRole userRole = new UserRole();
        userRole.setId(userRoleId);
        userRole.setUser(user);
        userRole.setRole(management);
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleRepository.saveAndFlush(userRole);

        HttpResponse<String> loginPage = send("/login", null, null);
        assertEquals(200, loginPage.statusCode());
        sessionCookie = updateSessionCookie(loginPage, null);

        HttpResponse<String> login = send("/login", sessionCookie, form(Map.of(
                "username", username,
                "password", password,
                "_csrf", csrfToken(loginPage.body()))));
        assertEquals(204, login.statusCode());
        sessionCookie = updateSessionCookie(login, sessionCookie);
    }

    @Test
    void apartmentMutationReturnsACompleteHtmlResponseOverEmbeddedTomcat() throws Exception {
        HttpResponse<String> registration = send("/management/resident-registration", sessionCookie, null);
        assertEquals(200, registration.statusCode());
        sessionCookie = updateSessionCookie(registration, sessionCookie);

        String token = UUID.randomUUID().toString().substring(0, 12);
        HttpResponse<String> response = send("/management/resident-registration/apartment", sessionCookie, form(Map.of(
                "_csrf", csrfToken(registration.body()),
                "building", "SYNTHETIC QA ONLY NV01 HTTP " + token,
                "apartmentCode", "HTTP-" + token,
                "floorNo", "8")));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Căn hộ đã được lưu"));
        assertTrue(response.body().contains("SYNTHETIC QA ONLY NV01 HTTP " + token));
    }

    private HttpResponse<String> send(String path, String cookie, String form) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (form == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String updateSessionCookie(HttpResponse<?> response, String previousCookie) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(SESSION_COOKIE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse(previousCookie);
    }

    private String csrfToken(String html) {
        Matcher matcher = CSRF_INPUT.matcher(html);
        assertTrue(matcher.find(), "Expected a server-rendered CSRF form token");
        return matcher.group(1);
    }

    private String form(Map<String, String> fields) {
        Map<String, String> encoded = new LinkedHashMap<>();
        fields.forEach((key, value) -> encoded.put(encode(key), encode(value)));
        return encoded.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
