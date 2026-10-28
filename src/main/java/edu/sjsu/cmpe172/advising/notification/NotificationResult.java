package edu.sjsu.cmpe172.advising.notification;

public record NotificationResult(boolean delivered, int attempts, String notificationId) {

    public static NotificationResult delivered(int attempts, String notificationId) {
        return new NotificationResult(true, attempts, notificationId);
    }

    public static NotificationResult notDelivered(int attempts) {
        return new NotificationResult(false, attempts, null);
    }
}
