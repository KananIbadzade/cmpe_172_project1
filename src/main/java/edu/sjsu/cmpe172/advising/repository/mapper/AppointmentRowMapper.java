package edu.sjsu.cmpe172.advising.repository.mapper;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class AppointmentRowMapper implements RowMapper<Appointment> {

    @Override
    public Appointment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Appointment(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getLong("provider_id"),
                rs.getLong("slot_id"),
                rs.getLong("service_id"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
