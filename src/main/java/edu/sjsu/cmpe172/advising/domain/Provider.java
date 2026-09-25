package edu.sjsu.cmpe172.advising.domain;

import java.time.OffsetDateTime;

public record Provider(
        Long id,
        long userId,
        String department,
        String officeLocation,
        OffsetDateTime createdAt
) {}
