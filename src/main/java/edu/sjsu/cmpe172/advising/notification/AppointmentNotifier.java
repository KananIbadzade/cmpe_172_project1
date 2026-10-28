package edu.sjsu.cmpe172.advising.notification;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.domain.User;
import edu.sjsu.cmpe172.advising.dto.NotificationRequest;
import edu.sjsu.cmpe172.advising.dto.NotificationType;
import edu.sjsu.cmpe172.advising.observability.BookingMetrics;
import edu.sjsu.cmpe172.advising.repository.SlotJdbcRepository;
import edu.sjsu.cmpe172.advising.repository.UserJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a committed appointment change into one coarse-grained notification.
 * Never throws: the appointment is already committed, so a notification problem
 * must not turn a successful booking into an error response.
 */
@Component
public class AppointmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotifier.class);

    private final NotificationGateway gateway;
    private final UserJdbcRepository userRepository;
    private final SlotJdbcRepository slotRepository;
    private final BookingMetrics metrics;

    public AppointmentNotifier(
            NotificationGateway gateway,
            UserJdbcRepository userRepository,
            SlotJdbcRepository slotRepository,
            BookingMetrics metrics) {
        this.gateway = gateway;
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.metrics = metrics;
    }

    public NotificationResult bookingConfirmed(Appointment appointment) {
        return notify(appointment, NotificationType.BOOKING_CONFIRMED, "is confirmed");
    }

    public NotificationResult bookingCancelled(Appointment appointment) {
        return notify(appointment, NotificationType.BOOKING_CANCELLED, "was cancelled");
    }

    private NotificationResult notify(Appointment appointment, NotificationType type, String outcome) {
        try {
            User student = userRepository.findById(appointment.customerId())
                    .orElseThrow(() -> new IllegalStateException("Student not found"));
            AvailabilitySlot slot = slotRepository.findById(appointment.slotId())
                    .orElseThrow(() -> new IllegalStateException("Slot not found"));

            String message = "Your advising appointment #%d on %s %s."
                    .formatted(appointment.id(), slot.startTime(), outcome);

            NotificationResult result = gateway.send(new NotificationRequest(
                    student.email(), type, message, idempotencyKey(appointment, type)));
            metrics.notificationResult(type, result.delivered());
            return result;
        } catch (RuntimeException ex) {
            log.error("notification skipped appointmentId={} type={} reason={}",
                    appointment.id(), type, ex.toString());
            metrics.notificationResult(type, false);
            return NotificationResult.notDelivered(0);
        }
    }

    /** Same appointment + same event always gives the same key, so retries are de-duplicated. */
    static String idempotencyKey(Appointment appointment, NotificationType type) {
        return "appointment-" + appointment.id() + "-" + type;
    }
}
