package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The /assistant page is for logged-in students only; its data comes from the JSON API. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AssistantPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void studentGetsThePageWithChatHistoryAndPolicyBoxes() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(get("/assistant").session(student))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chat-log\"")))
                .andExpect(content().string(containsString("id=\"policy-form\"")))
                .andExpect(content().string(containsString("data-history-url=\"/api/student/chat/history\"")))
                .andExpect(content().string(containsString("data-ask-url=\"/api/assistant/ask\"")));
    }

    @Test
    void anonymousVisitorsAreSentToLogin() throws Exception {
        mockMvc.perform(get("/assistant"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    @Test
    void advisorsAreForbidden() throws Exception {
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(get("/assistant").session(advisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void navLinkIsShownToStudentsOnly() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(get("/my-appointments").session(student))
                .andExpect(content().string(containsString("href=\"/assistant\"")));
        mockMvc.perform(get("/advisor").session(advisor))
                .andExpect(content().string(not(containsString("href=\"/assistant\""))));
    }
}
