package edu.sjsu.cmpe172.advising.domain;

import java.time.OffsetDateTime;

public record Appointment(
        Long id,
        long customerId,
        long providerId,
        long slotId,
        long serviceId,
        String status,
        String notes,
        OffsetDateTime createdAt
) {}
