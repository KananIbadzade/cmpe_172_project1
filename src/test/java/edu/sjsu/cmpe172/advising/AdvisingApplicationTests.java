package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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
                "availability_slots", 9,
                "appointments", 4);

        expectedRows.forEach((table, expected) -> {
            Integer actual = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(actual).as(table).isEqualTo(expected);
        });

        Integer booked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE status = 'BOOKED'", Integer.class);
        Integer cancelled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE status = 'CANCELLED'", Integer.class);
        Integer completed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE status = 'COMPLETED'", Integer.class);

        assertThat(booked).isEqualTo(2);
        assertThat(cancelled).isEqualTo(1);
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void secondActiveAppointmentForSameSlotIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO appointments (customer_id, provider_id, slot_id, service_id) VALUES (?, ?, ?, ?)",
                6, 1, 1, 1))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_appointment_active_slot");
    }

    @Test
    @Transactional
    void cancelledSlotCanBeRebooked() {
        // Slot 3 has a CANCELLED history row and is_booked = FALSE; a new BOOKED row must succeed.
        // Rollback keeps other tests on a clean seed.
        int rows = jdbcTemplate.update(
                "INSERT INTO appointments (customer_id, provider_id, slot_id, service_id, status) VALUES (?, ?, ?, ?, ?)",
                6, 1, 3, 1, "BOOKED");
        assertThat(rows).isEqualTo(1);
    }
}
