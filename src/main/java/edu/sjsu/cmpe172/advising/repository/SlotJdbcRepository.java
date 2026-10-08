package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.repository.mapper.SlotRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
     * Call only inside {@code @Transactional} booking/cancel flows.
     */
    public Optional<AvailabilitySlot> findByIdForUpdate(long id) {
        return jdbcTemplate.query(
                SELECT_SLOT + "WHERE id = ? FOR UPDATE",
                ROW_MAPPER,
                id)
                .stream()
                .findFirst();
    }

    public void setBooked(long id, boolean booked) {
        jdbcTemplate.update(
                "UPDATE availability_slots SET is_booked = ? WHERE id = ?",
                booked,
                id);
    }

    public long insert(long providerId, long serviceId, OffsetDateTime start, OffsetDateTime end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO availability_slots
                        (provider_id, service_id, start_time, end_time, is_booked)
                    VALUES (?, ?, ?, ?, FALSE)
                    """,
                    new String[] {"id"});
            ps.setLong(1, providerId);
            ps.setLong(2, serviceId);
            ps.setObject(3, start);
            ps.setObject(4, end);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return slot id");
        }
        return key.longValue();
    }

    /** Deletes an unbooked slot owned by the provider. Returns false if nothing deleted. */
    public boolean deleteUnbooked(long slotId, long providerId) {
        int rows = jdbcTemplate.update(
                """
                DELETE FROM availability_slots
                WHERE id = ? AND provider_id = ? AND is_booked = FALSE
                """,
                slotId,
                providerId);
        return rows > 0;
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
