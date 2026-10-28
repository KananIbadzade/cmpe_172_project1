package edu.sjsu.cmpe172.advising;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MockNotificationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsNotificationWithoutSession() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.notificationId").isNotEmpty());
    }

    @Test
    void repeatedIdempotencyKeyIsStoredOnce() throws Exception {
        String key = uniqueKey();

        MvcResult first = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andReturn();

        assertThat(idOf(second)).isEqualTo(idOf(first));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.request.idempotencyKey == '%s')]".formatted(key), hasSize(1)));
    }

    @Test
    void missingMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"alex.kim@sjsu.edu","type":"BOOKING_CONFIRMED","idempotencyKey":"%s"}
                                """.formatted(uniqueKey())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("message is required"));
    }

    private static String body(String key) {
        return """
                {"recipient":"alex.kim@sjsu.edu","type":"BOOKING_CONFIRMED",
                 "message":"Your advising appointment is booked.","idempotencyKey":"%s"}
                """.formatted(key);
    }

    private static String uniqueKey() {
        return "test-" + UUID.randomUUID();
    }

    private static String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.notificationId");
    }
}
