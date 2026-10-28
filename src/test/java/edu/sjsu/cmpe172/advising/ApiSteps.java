package edu.sjsu.cmpe172.advising;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared MockMvc steps for tests that log in and book through the JSON API. */
final class ApiSteps {

    static final String PASSWORD = "password123";

    private ApiSteps() {}

    static MockHttpSession login(MockMvc mockMvc, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Books the slot, expects 201, and returns the new appointment id. */
    static long book(MockMvc mockMvc, MockHttpSession session, long slotId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/student/appointments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":%d}".formatted(slotId)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
