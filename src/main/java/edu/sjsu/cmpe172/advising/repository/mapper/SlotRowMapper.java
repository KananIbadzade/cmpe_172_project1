package edu.sjsu.cmpe172.advising.repository.mapper;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class SlotRowMapper implements RowMapper<AvailabilitySlot> {

    @Override
    public AvailabilitySlot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AvailabilitySlot(
                rs.getLong("id"),
                rs.getLong("provider_id"),
                rs.getLong("service_id"),
                rs.getObject("start_time", OffsetDateTime.class),
                rs.getObject("end_time", OffsetDateTime.class),
                rs.getBoolean("is_booked"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
