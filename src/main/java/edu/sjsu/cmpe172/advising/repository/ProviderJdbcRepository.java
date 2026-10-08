package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.Provider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProviderJdbcRepository {

    private static final RowMapper<Provider> ROW_MAPPER = new ProviderRowMapper();

    private static final RowMapper<ProviderOption> OPTION_MAPPER = (rs, rowNum) -> new ProviderOption(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("department"));

    private final JdbcTemplate jdbcTemplate;

    public ProviderJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Provider> findById(long id) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, department, office_location, created_at
                FROM providers WHERE id = ?
                """,
                ROW_MAPPER,
                id).stream().findFirst();
    }

    public Optional<Provider> findByUserId(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, department, office_location, created_at
                FROM providers WHERE user_id = ?
                """,
                ROW_MAPPER,
                userId).stream().findFirst();
    }

    public List<ProviderOption> findOptions() {
        return jdbcTemplate.query(
                """
                SELECT p.id, u.full_name, p.department
                FROM providers p
                JOIN users u ON u.id = p.user_id
                ORDER BY u.full_name
                """,
                OPTION_MAPPER);
    }

    public record ProviderOption(long id, String fullName, String department) {}

    private static final class ProviderRowMapper implements RowMapper<Provider> {
        @Override
        public Provider mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Provider(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("department"),
                    rs.getString("office_location"),
                    rs.getObject("created_at", OffsetDateTime.class));
        }
    }
}
