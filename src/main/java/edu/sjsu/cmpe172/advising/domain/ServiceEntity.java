package edu.sjsu.cmpe172.advising.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ServiceEntity(
        Long id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        OffsetDateTime createdAt
) {}
