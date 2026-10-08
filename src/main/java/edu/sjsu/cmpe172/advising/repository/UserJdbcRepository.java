package edu.sjsu.cmpe172.advising.repository;

import edu.sjsu.cmpe172.advising.domain.User;
import edu.sjsu.cmpe172.advising.repository.mapper.UserRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserJdbcRepository {

    private static final UserRowMapper ROW_MAPPER = new UserRowMapper();

    private static final String SELECT_USER = """
            SELECT id, email, password_hash, full_name, role, created_at
            FROM users
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findById(long id) {
        return jdbcTemplate.query(SELECT_USER + "WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query(SELECT_USER + "WHERE email = ?", ROW_MAPPER, email)
                .stream()
                .findFirst();
    }
}
