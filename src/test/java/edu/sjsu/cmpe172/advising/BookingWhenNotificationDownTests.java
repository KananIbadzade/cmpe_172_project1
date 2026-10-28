package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 3 requirement: a booking still succeeds when the notification stub is down.
 * The outer class runs the real stub in DOWN mode (every call returns 503); the nested
 * class points the gateway at a port where nothing is listening.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.notification.base-url=",
                "app.notification.mock.mode=DOWN"
        })
@AutoConfigureMockMvc
class BookingWhenNotificationDownTests {

    private static final String STUDENT_EMAIL = "alex.kim@sjsu.edu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void bookingCommitsWhenStubReturns503() throws Exception {
        MockHttpSession session = ApiSteps.login(mockMvc, STUDENT_EMAIL);

        ApiSteps.book(mockMvc, session, 6);

        assertSlotBooked(jdbcTemplate, 6);
    }

    @Test
    void cancelCommitsWhenStubReturns503() throws Exception {
        MockHttpSession session = ApiSteps.login(mockMvc, STUDENT_EMAIL);
        long appointmentId = ApiSteps.book(mockMvc, session, 7);

        mockMvc.perform(post("/api/student/appointments/{id}/cancel", appointmentId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Boolean slotBooked = jdbcTemplate.queryForObject(
                "SELECT is_booked FROM availability_slots WHERE id = 7", Boolean.class);
        assertThat(slotBooked).isFalse();
    }

    @Nested
    @TestPropertySource(properties = "app.notification.base-url=http://localhost:1")
    class WhenStubIsUnreachable {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void bookingCommitsWhenNothingIsListening() throws Exception {
            MockHttpSession session = ApiSteps.login(mockMvc, STUDENT_EMAIL);

            ApiSteps.book(mockMvc, session, 3);

            assertSlotBooked(jdbcTemplate, 3);
        }
    }

    private static void assertSlotBooked(JdbcTemplate jdbcTemplate, long slotId) {
        Integer bookedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class, slotId);
        Boolean slotBooked = jdbcTemplate.queryForObject(
                "SELECT is_booked FROM availability_slots WHERE id = ?", Boolean.class, slotId);

        assertThat(bookedRows).isEqualTo(1);
        assertThat(slotBooked).isTrue();
    }
}
