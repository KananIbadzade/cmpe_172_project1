package edu.sjsu.cmpe172.advising.domain;

import java.time.OffsetDateTime;

/** A booked appointment that still needs its reminder, with everything the reminder mentions. */
public record ReminderCandidate(
        long appointmentId,
        String studentName,
        String studentEmail,
        String advisorName,
        String officeLocation,
        String serviceName,
        OffsetDateTime startTime
) {}
