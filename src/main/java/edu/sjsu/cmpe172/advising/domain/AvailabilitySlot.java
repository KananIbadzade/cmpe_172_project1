package edu.sjsu.cmpe172.advising.domain;

import java.time.OffsetDateTime;

public record AvailabilitySlot(
        Long id,
        long providerId,
        long serviceId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean booked,
        OffsetDateTime createdAt
) {}
