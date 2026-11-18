package edu.sjsu.cmpe172.advising.dto;

/** {@code pendingAction} is non-null while the assistant waits for the student to confirm a write. */
public record ChatResponse(String reply, PendingSummary pendingAction) {

    public record PendingSummary(String type, String summary) {}
}
