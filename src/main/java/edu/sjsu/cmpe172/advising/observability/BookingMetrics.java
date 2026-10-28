package edu.sjsu.cmpe172.advising.observability;

import edu.sjsu.cmpe172.advising.dto.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Application metrics, readable at /metrics/{name}:
 * <ul>
 *   <li>{@code advising.bookings} (tag outcome=success|conflict|failed): bookings attempted</li>
 *   <li>{@code advising.booking.latency}: time spent in the booking transaction</li>
 *   <li>{@code advising.notifications} (tags type, result=delivered|fallback): remote calls</li>
 * </ul>
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;
    private final Timer bookingLatency;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.bookingLatency = Timer.builder("advising.booking.latency")
                .description("Time spent in the booking transaction")
                .register(registry);
    }

    public <T> T timeBooking(Supplier<T> booking) {
        return bookingLatency.record(booking);
    }

    public void bookingOutcome(String outcome) {
        Counter.builder("advising.bookings")
                .description("Booking attempts by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void notificationResult(NotificationType type, boolean delivered) {
        Counter.builder("advising.notifications")
                .description("Notification calls by type and result")
                .tag("type", type.name())
                .tag("result", delivered ? "delivered" : "fallback")
                .register(registry)
                .increment();
    }
}
