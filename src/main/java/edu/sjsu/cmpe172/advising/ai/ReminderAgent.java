package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.domain.ReminderCandidate;
import edu.sjsu.cmpe172.advising.dto.NotificationRequest;
import edu.sjsu.cmpe172.advising.dto.NotificationType;
import edu.sjsu.cmpe172.advising.notification.NotificationGateway;
import edu.sjsu.cmpe172.advising.notification.NotificationResult;
import edu.sjsu.cmpe172.advising.observability.BookingMetrics;
import edu.sjsu.cmpe172.advising.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Agent 2, proactive: on a schedule (or when an advisor triggers it) it finds booked appointments
 * that start soon, has the LLM write a personalized reminder for each, and sends it through the
 * notification gateway. An appointment is stamped as reminded only after delivery, so a failed send
 * is retried on the next run. If the LLM is unavailable, a fixed template is sent instead.
 */
@Component
public class ReminderAgent {

    public record RunResult(int scanned, int sent, int failed) {}

    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a", Locale.US);

    private static final String SYSTEM = """
            You write short, friendly appointment reminders for a university academic advising office. \
            Use only the facts you are given. Write 2 or 3 sentences of plain text, with no subject line \
            and no markdown. Mention the advisor, the service, the time and the office location, \
            and mention the cancellation policy if one is given.
            """;

    private final AppointmentService appointments;
    private final NotificationGateway gateway;
    private final AiChat ai;
    private final KnowledgeBase knowledge;
    private final BookingMetrics metrics;
    private final Clock clock;
    private final boolean enabled;
    private final int windowHours;

    public ReminderAgent(
            AppointmentService appointments,
            NotificationGateway gateway,
            AiChat ai,
            KnowledgeBase knowledge,
            BookingMetrics metrics,
            Clock clock,
            @Value("${app.reminders.enabled}") boolean enabled,
            @Value("${app.reminders.window-hours}") int windowHours) {
        this.appointments = appointments;
        this.gateway = gateway;
        this.ai = ai;
        this.knowledge = knowledge;
        this.metrics = metrics;
        this.clock = clock;
        this.enabled = enabled;
        this.windowHours = windowHours;
    }

    @Scheduled(cron = "${app.reminders.cron}")
    void runScheduled() {
        if (enabled) {
            run(windowHours);
        }
    }

    /** One pass over appointments starting within the next {@code hours} hours. */
    public synchronized RunResult run(int hours) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ReminderCandidate> due = appointments.listDueForReminder(now, now.plusHours(hours));
        String policy = due.isEmpty() ? null : cancellationPolicy();
        int sent = 0;
        int failed = 0;
        for (ReminderCandidate candidate : due) {
            if (remind(candidate, policy)) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("reminder run scanned={} sent={} failed={} windowHours={}", due.size(), sent, failed, hours);
        return new RunResult(due.size(), sent, failed);
    }

    private boolean remind(ReminderCandidate candidate, String policy) {
        try {
            NotificationType type = NotificationType.APPOINTMENT_REMINDER;
            NotificationResult result = gateway.send(new NotificationRequest(
                    candidate.studentEmail(),
                    type,
                    writeReminder(candidate, policy),
                    "appointment-" + candidate.appointmentId() + "-" + type));
            metrics.notificationResult(type, result.delivered());
            if (result.delivered()) {
                appointments.markReminderSent(candidate.appointmentId());
            }
            return result.delivered();
        } catch (RuntimeException ex) {
            log.error("reminder failed appointmentId={} reason={}", candidate.appointmentId(), ex.toString());
            return false;
        }
    }

    /** The LLM's reminder, or the fixed template if the LLM is not configured or fails. */
    private String writeReminder(ReminderCandidate c, String policy) {
        String when = c.startTime().atZoneSameInstant(clock.getZone()).format(WHEN);
        try {
            String facts = """
                    Student: %s
                    Advisor: %s
                    Service: %s
                    When: %s
                    Location: %s
                    """.formatted(c.studentName(), c.advisorName(), c.serviceName(), when, c.officeLocation());
            if (policy != null) {
                facts += "Cancellation policy: " + policy + "\n";
            }
            String text = ai.client().prompt().system(SYSTEM).user(facts).call().content();
            if (text != null && !text.isBlank()) {
                metrics.reminderMessage("llm");
                return text.strip();
            }
        } catch (RuntimeException ex) {
            log.warn("reminder LLM unavailable appointmentId={}, using template: {}", c.appointmentId(), ex.toString());
        }
        metrics.reminderMessage("template");
        return "Hi %s, this is a reminder of your %s appointment with %s on %s at %s."
                .formatted(c.studentName(), c.serviceName(), c.advisorName(), when, c.officeLocation());
    }

    /** The cancellation section of the policy file, or null if the knowledge base is unavailable. */
    private String cancellationPolicy() {
        try {
            return knowledge.search("cancellation policy", 1).stream()
                    .map(Document::getText)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
