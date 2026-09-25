package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.repository.mapper.SlotRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SlotJdbcRepository {

    private static final SlotRowMapper ROW_MAPPER = new SlotRowMapper();

    private static final String SELECT_SLOT = """
            SELECT id, provider_id, service_id, start_time, end_time, is_booked, created_at
            FROM availability_slots
            """;

    private final JdbcTemplate jdbcTemplate;

    public SlotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AvailabilitySlot> findAvailable() {
        return jdbcTemplate.query(
                SELECT_SLOT + "WHERE is_booked = FALSE ORDER BY start_time, id",
                ROW_MAPPER);
    }

    public Optional<AvailabilitySlot> findById(long id) {
        return jdbcTemplate.query(SELECT_SLOT + "WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }
}
