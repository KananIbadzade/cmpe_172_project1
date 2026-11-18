package edu.sjsu.cmpe172.advising.ai;

import java.time.Instant;

/**
 * A write the assistant has proposed but the student has not confirmed yet.
 * {@code targetId} is the slot id for BOOK and the appointment id for CANCEL.
 */
public record PendingAction(
        Type type,
        long targetId,
        String notes,
        String summary,
        long userId,
        Instant createdAt
) {
    public enum Type { BOOK, CANCEL }
}
