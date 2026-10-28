package edu.sjsu.cmpe172.advising;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Book and cancel go through the real gateway to the mock service on this test's
 * random port, so the assertions read what the "remote" side actually received.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.notification.base-url=")
@AutoConfigureMockMvc
@Sql({"/schema.sql", "/seed.sql"})
class BookingNotificationTests {

    private static final String STUDENT_EMAIL = "alex.kim@sjsu.edu";
    private static final long FREE_SLOT_ID = 4;
    private static final long BOOKED_SLOT_ID = 5;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bookAndCancelEachSendExactlyOneNotification() throws Exception {
        MockHttpSession session = ApiSteps.login(mockMvc, STUDENT_EMAIL);

        long appointmentId = ApiSteps.book(mockMvc, session, FREE_SLOT_ID);
        List<String> confirmed = recipientsForKey("appointment-" + appointmentId + "-BOOKING_CONFIRMED");
        assertThat(confirmed).containsExactly(STUDENT_EMAIL);

        mockMvc.perform(post("/api/student/appointments/{id}/cancel", appointmentId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        List<String> cancelled = recipientsForKey("appointment-" + appointmentId + "-BOOKING_CANCELLED");
        assertThat(cancelled).containsExactly(STUDENT_EMAIL);
    }

    @Test
    void rolledBackBookingSendsNoNotification() throws Exception {
        MockHttpSession session = ApiSteps.login(mockMvc, STUDENT_EMAIL);
        int before = receivedCount();

        mockMvc.perform(post("/api/student/appointments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":%d}".formatted(BOOKED_SLOT_ID)))
                .andExpect(status().isConflict());

        assertThat(receivedCount()).isEqualTo(before);
    }

    private List<String> recipientsForKey(String key) throws Exception {
        return JsonPath.read(receivedJson(),
                "$[?(@.request.idempotencyKey == '%s')].request.recipient".formatted(key));
    }

    private int receivedCount() throws Exception {
        return JsonPath.read(receivedJson(), "$.length()");
    }

    private String receivedJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }
}
