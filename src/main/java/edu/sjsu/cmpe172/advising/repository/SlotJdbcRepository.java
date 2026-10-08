package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.repository.mapper.SlotRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
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

    public List<AvailabilitySlot> findAvailable(
            Long providerId, Long serviceId, LocalDate date, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT_SLOT);
        sql.append("WHERE is_booked = FALSE");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, providerId, serviceId, date);
        sql.append(" ORDER BY start_time, id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countAvailable(Long providerId, Long serviceId, LocalDate date) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM availability_slots WHERE is_booked = FALSE");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, providerId, serviceId, date);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public Optional<AvailabilitySlot> findById(long id) {
        return jdbcTemplate.query(SELECT_SLOT + "WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public List<AvailabilitySlot> findByProviderId(long providerId) {
        return jdbcTemplate.query(
                SELECT_SLOT + "WHERE provider_id = ? ORDER BY start_time, id",
                ROW_MAPPER,
                providerId);
    }

    /**
     * Locks the slot row until the surrounding transaction commits.
     * Call only inside {@code @Transactional} booking/cancel flows (Phase 4).
     */
    public Optional<AvailabilitySlot> findByIdForUpdate(long id) {
        return jdbcTemplate.query(
                SELECT_SLOT + "WHERE id = ? FOR UPDATE",
                ROW_MAPPER,
                id)
                .stream()
                .findFirst();
    }

    private static void appendFilters(
            StringBuilder sql, List<Object> args, Long providerId, Long serviceId, LocalDate date) {
        if (providerId != null) {
            sql.append(" AND provider_id = ?");
            args.add(providerId);
        }
        if (serviceId != null) {
            sql.append(" AND service_id = ?");
            args.add(serviceId);
        }
        if (date != null) {
            sql.append(" AND CAST(start_time AS DATE) = ?");
            args.add(Date.valueOf(date));
        }
    }
}
