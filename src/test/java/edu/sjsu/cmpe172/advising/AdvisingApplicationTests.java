package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AdvisingApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startupScriptsCreateAndSeedAllTables() {
        Map<String, Integer> expectedRows = Map.of(
                "users", 6,
                "providers", 3,
                "services", 4,
                "availability_slots", 8,
                "appointments", 2);

        expectedRows.forEach((table, expected) -> {
            Integer actual = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(actual).as(table).isEqualTo(expected);
        });
    }

    @Test
    void secondAppointmentForSameSlotIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO appointments (customer_id, provider_id, slot_id, service_id) VALUES (?, ?, ?, ?)",
                6, 1, 1, 1))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_appointment_active_slot");
    }
}
