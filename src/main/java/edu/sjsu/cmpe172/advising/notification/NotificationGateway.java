package edu.sjsu.cmpe172.advising.notification;

import edu.sjsu.cmpe172.advising.dto.NotificationRequest;

/**
 * The scheduler's only view of the remote notification service.
 * Implementations never throw: a failed delivery comes back as a result, so callers
 * (booking, cancel) cannot be broken by the remote side.
 */
public interface NotificationGateway {

    NotificationResult send(NotificationRequest request);
}
