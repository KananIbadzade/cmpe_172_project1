package edu.sjsu.cmpe172.advising.notification;

import edu.sjsu.cmpe172.advising.dto.NotificationRequest;
import edu.sjsu.cmpe172.advising.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * HTTP client for the notification service with a timeout, retry, and fallback.
 * <ul>
 *   <li>Timeout: connect and read limits so a slow service cannot hang the caller.</li>
 *   <li>Retry: timeouts, connection errors, and 5xx are retried with a growing pause;
 *       4xx is not, since resending a bad request cannot succeed.</li>
 *   <li>Fallback: after the last attempt, log and return "not delivered" instead of throwing.</li>
 * </ul>
 * Retries are safe because every request carries the same idempotency key.
 */
@Component
public class HttpNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final RestClient restClient;
    private final Environment environment;
    private final String configuredBaseUrl;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public HttpNotificationGateway(
            RestClient.Builder builder,
            Environment environment,
            @Value("${app.notification.base-url}") String configuredBaseUrl,
            @Value("${app.notification.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.notification.read-timeout-ms}") long readTimeoutMs,
            @Value("${app.notification.max-attempts}") int maxAttempts,
            @Value("${app.notification.retry-backoff-ms}") long retryBackoffMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = builder.requestFactory(requestFactory).build();
        this.environment = environment;
        this.configuredBaseUrl = configuredBaseUrl;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = retryBackoffMs;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        String url = baseUrl() + "/api/notifications";

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                NotificationResponse response = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(NotificationResponse.class);

                String id = response == null ? null : response.notificationId();
                log.info("notification delivered key={} id={} attempts={}",
                        request.idempotencyKey(), id, attempt);
                return NotificationResult.delivered(attempt, id);
            } catch (HttpClientErrorException ex) {
                log.error("notification rejected key={} status={} - not retrying",
                        request.idempotencyKey(), ex.getStatusCode().value());
                return NotificationResult.notDelivered(attempt);
            } catch (RestClientException ex) {
                log.warn("notification attempt {}/{} failed key={} reason={}",
                        attempt, maxAttempts, request.idempotencyKey(), reason(ex));
            }

            if (attempt < maxAttempts && !pauseBeforeRetry(attempt)) {
                return fallback(request, attempt);
            }
        }
        return fallback(request, maxAttempts);
    }

    private NotificationResult fallback(NotificationRequest request, int attempts) {
        log.error("notification fallback key={} attempts={} - not delivered, caller continues",
                request.idempotencyKey(), attempts);
        return NotificationResult.notDelivered(attempts);
    }

    /** Waits 1x, 2x, 4x... the base backoff. Returns false if the thread was interrupted. */
    private boolean pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBackoffMs * (1L << (attempt - 1)));
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** "status=503" for HTTP errors, otherwise the root cause such as a read timeout. */
    private static String reason(RestClientException ex) {
        if (ex instanceof HttpStatusCodeException statusEx) {
            return "status=" + statusEx.getStatusCode().value();
        }
        return ex.getMostSpecificCause().toString();
    }

    /** A blank base URL means the mock in this same app, on whatever port the server got. */
    private String baseUrl() {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }
}
