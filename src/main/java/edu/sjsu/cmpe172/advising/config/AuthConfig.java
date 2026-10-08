package edu.sjsu.cmpe172.advising.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 10 matches the seeded password hashes in seed.sql.
        return new BCryptPasswordEncoder(10);
    }
}
