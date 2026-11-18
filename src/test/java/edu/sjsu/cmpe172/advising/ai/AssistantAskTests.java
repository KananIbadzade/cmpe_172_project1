package edu.sjsu.cmpe172.advising.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/assistant/ask with a fake embedding model and a scripted chat model that records the
 * prompts it receives, so we can see that retrieved policy text really reaches the model.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantAskTests {

    static final List<String> PROMPTS = new ArrayList<>();

    @TestConfiguration
    static class FakeModels {
        @Bean
        EmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }

        @Bean
        ChatModel recordingChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    PROMPTS.add(prompt.getContents());
                    return new ChatResponse(List.of(new Generation(
                            new AssistantMessage("Please cancel at least 24 hours before your appointment."))));
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdvisingTools tools;

    @Test
    void answersAreGroundedInRetrievedPolicyText() throws Exception {
        PROMPTS.clear();

        // No login: the policy FAQ is public.
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How early do I need to cancel an appointment?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Please cancel at least 24 hours before your appointment."))
                .andExpect(jsonPath("$.sources[0]").value("Cancellation policy"));

        assertThat(PROMPTS).hasSize(1);
        assertThat(PROMPTS.get(0))
                .contains("Use only the context provided")
                .contains("cancel at least 24 hours before the start time");
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatAgentCanLookUpPolicies() {
        assertThat(tools.searchPolicies("What should I bring to a graduation check?"))
                .contains("degree progress report");
    }
}
