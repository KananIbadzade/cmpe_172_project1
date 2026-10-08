package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.ServiceEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ServiceJdbcRepository {

    private static final RowMapper<ServiceEntity> ROW_MAPPER = new ServiceRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public ServiceJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ServiceEntity> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, name, description, duration_minutes, price, created_at
                FROM services
                ORDER BY name
                """,
                ROW_MAPPER);
    }

    public Optional<ServiceEntity> findById(long id) {
        return jdbcTemplate.query(
                """
                SELECT id, name, description, duration_minutes, price, created_at
                FROM services WHERE id = ?
                """,
                ROW_MAPPER,
                id).stream().findFirst();
    }

    private static final class ServiceRowMapper implements RowMapper<ServiceEntity> {
        @Override
        public ServiceEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ServiceEntity(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("duration_minutes"),
                    rs.getObject("price", BigDecimal.class),
                    rs.getObject("created_at", OffsetDateTime.class));
        }
    }
}
