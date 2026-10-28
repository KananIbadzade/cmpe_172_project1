package edu.sjsu.cmpe172.advising.dto;

/**
 * Coarse-grained payload sent across the notification boundary: one call carries
 * everything the remote service needs. {@code idempotencyKey} lets the receiver
 * drop duplicates when the client retries.
 */
public record NotificationRequest(
        String recipient,
        NotificationType type,
        String message,
        String idempotencyKey
) {}
