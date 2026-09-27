package vn.edu.huit.smartparking.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToSmartParkingDatabase() {
        assertEquals("smart_parking", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }

}
