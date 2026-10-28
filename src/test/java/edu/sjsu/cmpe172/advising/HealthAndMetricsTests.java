package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthAndMetricsTests {

    private static final String STUDENT_EMAIL = "jordan.lee@sjsu.edu";
    private static final String ADVISOR_EMAIL = "maria.chen@sjsu.edu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthIsPublicAndChecksTheDatabase() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.database").value("PostgreSQL"));
    }

    @Test
    void metricsRequireAdvisorRole() throws Exception {
        mockMvc.perform(get("/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication required"));
        mockMvc.perform(get("/metrics/advising.bookings"))
                .andExpect(status().isUnauthorized());

        MockHttpSession student = ApiSteps.login(mockMvc, STUDENT_EMAIL);
        mockMvc.perform(get("/metrics").session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Advisor role required"));
    }

    @Test
    void bookingOutcomesAndLatencyAreRecorded() throws Exception {
        // This context is shared with other test classes, so use a fresh slot instead of a seed one.
        Long slotId = jdbcTemplate.queryForObject("""
                INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
                VALUES (1, 1, CURRENT_DATE + 40 + TIME '08:00', CURRENT_DATE + 40 + TIME '08:30')
                RETURNING id
                """, Long.class);

        MockHttpSession student = ApiSteps.login(mockMvc, STUDENT_EMAIL);
        ApiSteps.book(mockMvc, student, slotId);
        mockMvc.perform(post("/api/student/appointments")
                        .session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":%d}".formatted(slotId)))
                .andExpect(status().isConflict());

        MockHttpSession advisor = ApiSteps.login(mockMvc, ADVISOR_EMAIL);

        mockMvc.perform(get("/metrics/advising.bookings").param("tag", "outcome:success").session(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
        mockMvc.perform(get("/metrics/advising.bookings").param("tag", "outcome:conflict").session(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
        mockMvc.perform(get("/metrics/advising.booking.latency").session(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value").exists());
        // Test config points notifications at a closed port, so the booking used the fallback.
        mockMvc.perform(get("/metrics/advising.notifications").param("tag", "result:fallback").session(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
    }
}
