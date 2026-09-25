package edu.sjsu.cmpe172.advising.dto;

import java.time.OffsetDateTime;

public record SlotResponse(
        long id,
        long providerId,
        long serviceId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean booked
) {}
