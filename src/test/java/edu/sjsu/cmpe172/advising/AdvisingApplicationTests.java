package edu.sjsu.cmpe172.advising;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AdvisingApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void restartKeepsExistingBookings() throws IOException {
        // A new booking that is not part of the seed data.
        jdbcTemplate.update(
                "INSERT INTO appointments (customer_id, provider_id, slot_id, service_id, notes) VALUES (?, ?, ?, ?, ?)",
                6, 1, 2, 4, "survives restart");

        // Every startup runs migrate(); on an up-to-date database it must apply nothing.
        int executed = flyway.migrate().migrationsExecuted;

        assertThat(executed).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE notes = 'survives restart'", Integer.class))
                .isEqualTo(1);
        // Every migration file has been applied exactly once, however many there are.
        int migrationFiles = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql").length;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(migrationFiles);
    }

    @Test
    void migrationsCreateAndSeedAllTables() {
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
