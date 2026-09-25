package edu.sjsu.cmpe172.advising.dto;

import java.time.Instant;

public record SystemStatusResponse(
        String system,
        String status,
        String environment,
        Instant timestamp,
        String milestone
) {}
