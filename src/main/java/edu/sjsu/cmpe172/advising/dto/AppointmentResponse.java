package edu.sjsu.cmpe172.advising.dto;

import edu.sjsu.cmpe172.advising.domain.Appointment;

import java.time.OffsetDateTime;

public record AppointmentResponse(
        long id,
        long customerId,
        long providerId,
        long slotId,
        long serviceId,
        String status,
        String notes,
        OffsetDateTime createdAt
) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.id(),
                appointment.customerId(),
                appointment.providerId(),
                appointment.slotId(),
                appointment.serviceId(),
                appointment.status(),
                appointment.notes(),
                appointment.createdAt());
    }
}
