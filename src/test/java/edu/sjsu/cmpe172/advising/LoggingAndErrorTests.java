package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks WARN lines from the logging plan and that framework errors keep their 4xx status. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Sql({"/schema.sql", "/seed.sql"})
class LoggingAndErrorTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void failedLoginLogsWarnWithoutPassword(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex.kim@sjsu.edu\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(output).contains("WARN").contains("login failed email=alex.kim@sjsu.edu");
        assertThat(output).doesNotContain("not-the-password");
    }

    @Test
    void bookingConflictLogsWarn(CapturedOutput output) throws Exception {
        MockHttpSession session = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        // Slot 5 is booked in seed.sql.
        mockMvc.perform(post("/api/student/appointments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":5}"))
                .andExpect(status().isConflict());

        assertThat(output).contains("booking rejected slotId=5");
    }

    @Test
    void malformedJsonStays400() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
