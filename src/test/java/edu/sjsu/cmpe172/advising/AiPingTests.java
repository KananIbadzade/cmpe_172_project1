package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests run with no Gemini key, so the AI endpoints must degrade to 503 and never call out. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AiPingTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturns503ForStudentWhenAiNotConfigured() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(get("/api/student/chat/ping").session(student))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("AI assistant is not configured"));
    }

    @Test
    void pingIsForbiddenForAdvisor() throws Exception {
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(get("/api/student/chat/ping").session(advisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void pingRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/student/chat/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatReturns503ForStudentWhenAiNotConfigured() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(chat(student, "Find me a slot"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI assistant is not configured"));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(chat(student, "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatIsForbiddenForAdvisor() throws Exception {
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(chat(advisor, "hello"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder chat(MockHttpSession session, String message) {
        return post("/api/student/chat")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"%s\"}".formatted(message));
    }
}
