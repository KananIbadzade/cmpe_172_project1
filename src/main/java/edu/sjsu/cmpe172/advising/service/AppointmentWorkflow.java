package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.notification.AppointmentNotifier;
import edu.sjsu.cmpe172.advising.observability.BookingMetrics;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import org.springframework.stereotype.Service;

/**
 * Commit first, then notify. {@link AppointmentService} owns the transaction; by the
 * time its method returns, PostgreSQL has committed and the connection is back in the
 * pool. Only then do we make the remote call.
 * <p>
 * This class must not be {@code @Transactional}: wrapping it would move the commit
 * after the remote call.
 */
@Service
public class AppointmentWorkflow {

    private final AppointmentService appointmentService;
    private final AppointmentNotifier notifier;
    private final BookingMetrics metrics;

    public AppointmentWorkflow(
            AppointmentService appointmentService,
            AppointmentNotifier notifier,
            BookingMetrics metrics) {
        this.appointmentService = appointmentService;
        this.notifier = notifier;
        this.metrics = metrics;
    }

    public Appointment book(long slotId, long customerId, String notes) {
        Appointment appointment;
        try {
            appointment = metrics.timeBooking(() -> appointmentService.book(slotId, customerId, notes));
            metrics.bookingOutcome("success");
        } catch (SlotConflictException ex) {
            metrics.bookingOutcome("conflict");
            throw ex;
        } catch (RuntimeException ex) {
            metrics.bookingOutcome("failed");
            throw ex;
        }

        notifier.bookingConfirmed(appointment);
        return appointment;
    }

    public Appointment cancel(long appointmentId, long customerId) {
        Appointment appointment = appointmentService.cancel(appointmentId, customerId);
        notifier.bookingCancelled(appointment);
        return appointment;
    }
}
