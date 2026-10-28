package edu.sjsu.cmpe172.advising.mocknotification;

import edu.sjsu.cmpe172.advising.dto.NotificationRequest;
import edu.sjsu.cmpe172.advising.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for an external messaging provider. It lives in this app for the course,
 * but the scheduler only reaches it over HTTP, never by calling this class directly.
 * Nothing is sent; notifications are logged and kept in memory.
 */
@RestController
@RequestMapping("/api/notifications")
public class MockNotificationController {

    public enum Mode { UP, DOWN, SLOW }

    public record ReceivedNotification(String notificationId, NotificationRequest request, Instant receivedAt) {}

    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);

    private final Map<String, ReceivedNotification> receivedByKey = new ConcurrentHashMap<>();
    private final Mode mode;
    private final long slowDelayMs;

    public MockNotificationController(
            @Value("${app.notification.mock.mode}") Mode mode,
            @Value("${app.notification.mock.slow-delay-ms}") long slowDelayMs) {
        this.mode = mode;
        this.slowDelayMs = slowDelayMs;
    }

    @PostMapping
    public ResponseEntity<?> receive(@RequestBody NotificationRequest request) throws InterruptedException {
        if (mode == Mode.DOWN) {
            log.warn("mock-notification simulated outage, rejecting key={}", keyOf(request));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Notification service unavailable"));
        }
        if (mode == Mode.SLOW) {
            Thread.sleep(slowDelayMs);
        }

        String problem = validate(request);
        if (problem != null) {
            return ResponseEntity.badRequest()
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, problem));
        }

        ReceivedNotification fresh = new ReceivedNotification(
                UUID.randomUUID().toString(), request, Instant.now());
        ReceivedNotification existing = receivedByKey.putIfAbsent(request.idempotencyKey(), fresh);

        if (existing != null) {
            log.info("mock-notification duplicate ignored key={} id={}",
                    request.idempotencyKey(), existing.notificationId());
            return ResponseEntity.ok(new NotificationResponse(existing.notificationId(), "ACCEPTED", true));
        }

        log.info("mock-notification accepted key={} type={} recipient={} id={}",
                request.idempotencyKey(), request.type(), request.recipient(), fresh.notificationId());
        return ResponseEntity.ok(new NotificationResponse(fresh.notificationId(), "ACCEPTED", false));
    }

    @GetMapping
    public List<ReceivedNotification> list() {
        return receivedByKey.values().stream()
                .sorted(Comparator.comparing(ReceivedNotification::receivedAt))
                .toList();
    }

    private static String validate(NotificationRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (isBlank(request.recipient())) {
            return "recipient is required";
        }
        if (request.type() == null) {
            return "type is required";
        }
        if (isBlank(request.message())) {
            return "message is required";
        }
        if (isBlank(request.idempotencyKey())) {
            return "idempotencyKey is required";
        }
        return null;
    }

    private static String keyOf(NotificationRequest request) {
        return request == null ? null : request.idempotencyKey();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
