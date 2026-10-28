package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.User;
import edu.sjsu.cmpe172.advising.repository.UserJdbcRepository;
import edu.sjsu.cmpe172.advising.service.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserJdbcRepository userJdbcRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserJdbcRepository userJdbcRepository, PasswordEncoder passwordEncoder) {
        this.userJdbcRepository = userJdbcRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies email + password against PostgreSQL BCrypt hashes. Never logs or returns the hash.
     */
    public SessionUser authenticate(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        User user = userJdbcRepository.findByEmail(email.trim().toLowerCase())
                .or(() -> userJdbcRepository.findByEmail(email.trim()))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
            log.warn("login failed email={}", email.trim());
            throw new InvalidCredentialsException();
        }

        log.info("login succeeded userId={} role={}", user.id(), user.role());
        return SessionUser.from(user);
    }
}
