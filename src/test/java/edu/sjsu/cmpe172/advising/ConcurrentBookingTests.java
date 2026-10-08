package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two students race to book the same free slot over HTTP.
 * Exactly one must get 201 Created; the other must get 409 Conflict.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentBookingTests {

    private static final long SLOT_ID = 8L;
    private static final String PASSWORD = "password123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreSlot() {
        jdbcTemplate.update(
                "DELETE FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                SLOT_ID);
        jdbcTemplate.update(
                "UPDATE availability_slots SET is_booked = FALSE WHERE id = ?",
                SLOT_ID);
    }

    @Test
    void twoConcurrentBookingsProduceOneSuccessAndOneConflict() throws Exception {
        jdbcTemplate.update(
                "UPDATE availability_slots SET is_booked = FALSE WHERE id = ?",
                SLOT_ID);
        jdbcTemplate.update(
                "DELETE FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                SLOT_ID);

        HttpClient alex = loginClient("alex.kim@sjsu.edu");
        HttpClient jordan = loginClient("jordan.lee@sjsu.edu");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> alexFuture = pool.submit(
                    () -> runBook(alex, ready, start, created, conflict));
            Future<?> jordanFuture = pool.submit(
                    () -> runBook(jordan, ready, start, created, conflict));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            alexFuture.get(10, TimeUnit.SECONDS);
            jordanFuture.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(created.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        Integer bookedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class,
                SLOT_ID);
        assertThat(bookedRows).isEqualTo(1);
    }

    private void runBook(
            HttpClient client,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger created,
            AtomicInteger conflict) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/student/appointments"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"slotId\":" + SLOT_ID + ",\"notes\":\"Concurrent booking race\"}"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                created.incrementAndGet();
            } else if (response.statusCode() == 409) {
                conflict.incrementAndGet();
            } else {
                throw new IllegalStateException(
                        "Unexpected status " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Concurrent booking request failed", ex);
        }
    }

    private HttpClient loginClient(String email) throws Exception {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest login = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();

        HttpResponse<String> response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return client;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
