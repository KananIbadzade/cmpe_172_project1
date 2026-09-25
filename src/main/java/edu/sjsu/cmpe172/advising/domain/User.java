package edu.sjsu.cmpe172.advising.domain;

import java.time.OffsetDateTime;

public record User(
        Long id,
        String email,
        String passwordHash,
        String fullName,
        String role,
        OffsetDateTime createdAt
) {}
