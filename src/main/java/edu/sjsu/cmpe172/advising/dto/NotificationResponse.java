package edu.sjsu.cmpe172.advising.dto;

public record NotificationResponse(String notificationId, String status, boolean duplicate) {}
