package edu.sjsu.cmpe172.advising.domain;

/**
 * Appointment lifecycle values stored in {@code appointments.status}.
 * Matches the PostgreSQL CHECK constraint in schema.sql.
 */
public enum AppointmentStatus {
    BOOKED,
    CANCELLED,
    COMPLETED;

    public static AppointmentStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Appointment status is required");
        }
        return AppointmentStatus.valueOf(value.trim().toUpperCase());
    }
}
