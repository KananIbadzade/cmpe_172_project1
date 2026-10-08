package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentEndpointTests {

    private static final String ALEX = "alex.kim@sjsu.edu";
    private static final String JORDAN = "jordan.lee@sjsu.edu";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void studentCanBookAvailableSlot() throws Exception {
        MockHttpSession session = login(ALEX);

        mockMvc.perform(post("/api/student/appointments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":2,"notes":"Need course plan help"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(2))
                .andExpect(jsonPath("$.customerId").value(4))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        Boolean booked = jdbcTemplate.queryForObject(
                "SELECT is_booked FROM availability_slots WHERE id = 2", Boolean.class);
        assertThat(booked).isTrue();
    }

    @Test
    @Transactional
    void bookingAlreadyTakenSlotReturns409() throws Exception {
        MockHttpSession session = login(ALEX);

        mockMvc.perform(post("/api/student/appointments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":1,"notes":"Should conflict"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @Transactional
    void ownerCanCancelOwnAppointment() throws Exception {
        MockHttpSession session = login(ALEX);

        mockMvc.perform(post("/api/student/appointments/1/cancel").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Boolean booked = jdbcTemplate.queryForObject(
                "SELECT is_booked FROM availability_slots WHERE id = 1", Boolean.class);
        assertThat(booked).isFalse();
    }

    @Test
    @Transactional
    void nonOwnerCancelReturns403() throws Exception {
        MockHttpSession session = login(JORDAN);

        mockMvc.perform(post("/api/student/appointments/1/cancel").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Only the appointment owner can cancel"));
    }

    @Test
    void myAppointmentsRequiresStudentSession() throws Exception {
        mockMvc.perform(get("/api/student/appointments"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = login(ALEX);
        mockMvc.perform(get("/api/student/appointments").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(4));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
