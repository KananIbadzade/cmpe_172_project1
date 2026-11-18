package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.FakeEmbeddingModel;
import edu.sjsu.cmpe172.advising.ai.PendingActionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When Gemini misbehaves (quota exceeded, network down, request blocked) the AI endpoints must say
 * so with a clear 503, not a generic 500, and an empty model answer must not cause a null pointer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AiUnavailableTests {

    /** What the stub model does next: "fail" throws like a provider error, "empty" returns no answer. */
    static volatile String mode = "fail";

    @TestConfiguration
    static class MisbehavingModel {
        @Bean
        EmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }

        @Bean
        ChatModel misbehavingChatModel() {
            ToolCallingManager toolCalling = ToolCallingManager.builder().build();
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    if ("proposeThenFail".equals(mode)) {
                        // The model records a proposal through the real tool, then the provider fails.
                        toolCalling.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(
                                AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1", "function", "requestBooking", "{\"slotId\":2}"))).build()))));
                    }
                    if (!"empty".equals(mode)) {
                        throw new IllegalStateException("429 RESOURCE_EXHAUSTED: quota exceeded");
                    }
                    return new ChatResponse(List.of());
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PendingActionStore pendingActions;

    @AfterEach
    void resetMode() {
        mode = "fail";
    }

    @Test
    void providerFailureIsReportedAs503() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(chat(student)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI assistant is temporarily unavailable. Please try again."));
        mockMvc.perform(ask()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI assistant is temporarily unavailable. Please try again."));
        mockMvc.perform(get("/api/student/chat/ping").session(student)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void emptyModelAnswerDoesNotCauseANullPointer() throws Exception {
        mode = "empty";
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(chat(student)).andExpect(status().isOk());
        mockMvc.perform(ask()).andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Sorry, I could not answer that. Please contact the advising office."));
        mockMvc.perform(get("/api/student/chat/ping").session(student)).andExpect(status().isOk());
    }

    @Test
    void aProposalRecordedJustBeforeAProviderFailureStaysClosed() throws Exception {
        mode = "proposeThenFail";
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(chat(student)).andExpect(status().isServiceUnavailable());

        // The student saw only an error, so there must be nothing left for a later "yes" to confirm.
        assertThat(pendingActions.find("student-6")).isEmpty();
    }

    private static MockHttpServletRequestBuilder chat(MockHttpSession session) {
        return post("/api/student/chat").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}");
    }

    private static MockHttpServletRequestBuilder ask() {
        return post("/api/assistant/ask")
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"When are you open?\"}");
    }
}
