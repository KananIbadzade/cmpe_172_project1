package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.domain.AppointmentStatus;
import edu.sjsu.cmpe172.advising.repository.mapper.AppointmentRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentJdbcRepository {

    private static final AppointmentRowMapper ROW_MAPPER = new AppointmentRowMapper();

    private static final String SELECT_APPOINTMENT = """
            SELECT id, customer_id, provider_id, slot_id, service_id, status, notes, created_at
            FROM appointments
            """;

    private final JdbcTemplate jdbcTemplate;

    public AppointmentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Appointment> findById(long id) {
        return jdbcTemplate.query(SELECT_APPOINTMENT + "WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public List<Appointment> findByCustomerId(long customerId) {
        return jdbcTemplate.query(
                SELECT_APPOINTMENT + "WHERE customer_id = ? ORDER BY created_at DESC, id DESC",
                ROW_MAPPER,
                customerId);
    }

    public List<Appointment> findByProviderId(long providerId) {
        return jdbcTemplate.query(
                SELECT_APPOINTMENT + "WHERE provider_id = ? ORDER BY created_at DESC, id DESC",
                ROW_MAPPER,
                providerId);
    }

    /** Active booking for a slot, if any (status = BOOKED). */
    public Optional<Appointment> findActiveBySlotId(long slotId) {
        return jdbcTemplate.query(
                SELECT_APPOINTMENT + "WHERE slot_id = ? AND status = ? ORDER BY id",
                ROW_MAPPER,
                slotId,
                AppointmentStatus.BOOKED.name())
                .stream()
                .findFirst();
    }
}
