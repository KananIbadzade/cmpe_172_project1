package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.dto.NotificationRequest;
import edu.sjsu.cmpe172.advising.dto.NotificationType;
import edu.sjsu.cmpe172.advising.notification.NotificationGateway;
import edu.sjsu.cmpe172.advising.notification.NotificationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real mock service over HTTP on a random port. Each nested class gets its
 * own context so the mock can be switched to DOWN or SLOW.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.notification.max-attempts=3",
                "app.notification.retry-backoff-ms=50"
        })
class HttpNotificationGatewayTests {

    @Autowired
    private NotificationGateway gateway;

    @Test
    void deliversOnFirstAttemptWhenServiceIsUp() {
        NotificationResult result = gateway.send(request("Your appointment is booked."));

        assertThat(result.delivered()).isTrue();
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.notificationId()).isNotBlank();
    }

    @Test
    void doesNotRetryARejectedRequest() {
        NotificationResult result = gateway.send(request(""));

        assertThat(result.delivered()).isFalse();
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Nested
    @TestPropertySource(properties = "app.notification.mock.mode=DOWN")
    class WhenServiceIsDown {

        @Autowired
        private NotificationGateway gateway;

        @Test
        void retriesThenFallsBackWithoutThrowing() {
            NotificationResult result = gateway.send(request("Your appointment is booked."));

            assertThat(result.delivered()).isFalse();
            assertThat(result.attempts()).isEqualTo(3);
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "app.notification.mock.mode=SLOW",
            "app.notification.mock.slow-delay-ms=1000",
            "app.notification.read-timeout-ms=200"
    })
    class WhenServiceIsSlow {

        @Autowired
        private NotificationGateway gateway;

        @Test
        void timesOutEachAttemptThenFallsBack() {
            long start = System.nanoTime();
            NotificationResult result = gateway.send(request("Your appointment is booked."));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(result.delivered()).isFalse();
            assertThat(result.attempts()).isEqualTo(3);
            // 3 x 200 ms timeouts + 50 + 100 ms pauses, well under the 3 x 1000 ms the mock would take.
            assertThat(elapsedMs).isLessThan(2000);
        }
    }

    private static NotificationRequest request(String message) {
        return new NotificationRequest(
                "alex.kim@sjsu.edu",
                NotificationType.BOOKING_CONFIRMED,
                message,
                "gateway-test-" + UUID.randomUUID());
    }
}
