package edu.sjsu.cmpe172.advising;

import com.jayway.jsonpath.JsonPath;
import edu.sjsu.cmpe172.advising.ai.ReminderAgent;
import edu.sjsu.cmpe172.advising.ai.ReminderAgent.RunResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** If the LLM call fails, the agent still sends a reminder, using the fixed template. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.notification.base-url=")
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class ReminderFallbackTests {

    @TestConfiguration
    static class FailingModel {
        @Bean
        ChatModel failingChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    throw new IllegalStateException("Gemini is down");
                }
            };
        }
    }

    @Autowired
    private ReminderAgent agent;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void templateReminderIsSentWhenTheLlmFails() throws Exception {
        assertThat(agent.run(72)).isEqualTo(new RunResult(2, 2, 0));

        String json = mockMvc.perform(get("/api/notifications")).andReturn().getResponse().getContentAsString();
        List<String> messages = JsonPath.read(json,
                "$[?(@.request.idempotencyKey == 'appointment-1-APPOINTMENT_REMINDER')].request.message");
        assertThat(messages).singleElement().asString()
                .startsWith("Hi Alex Kim, this is a reminder of your Graduation Check appointment with Maria Chen on ")
                .endsWith(" at ENG 281.");
    }
}
