package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real ChatClient pipeline (system prompt, memory, tools, tool context) with a scripted model
 * in place of Gemini. The model asks for requestBooking(slot 2), Spring AI runs the tool with the
 * student's ToolContext, and the model then answers in text. No network.
 */
@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class ChatServiceLlmFlowTests {

    private static final SessionUser SAM = new SessionUser(6, "sam.rivera@sjsu.edu", "Sam Rivera", UserRole.STUDENT);

    @TestConfiguration
    static class ScriptedModel {
        @Bean
        ChatModel scriptedChatModel() {
            ToolCallingManager toolCalling = ToolCallingManager.builder().build();
            return new ChatModel() {
                @Override
                public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
                    AssistantMessage toolCall = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "call-1", "function", "requestBooking", "{\"slotId\":2}")))
                            .build();
                    // Runs the tool the way a real model integration does, with the prompt's tool context.
                    toolCalling.executeToolCalls(prompt, new org.springframework.ai.chat.model.ChatResponse(
                            List.of(new Generation(toolCall))));
                    return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(
                            new AssistantMessage("I can book Course Planning for you. Reply yes to confirm."))));
                }

                @Override
                public ChatOptions getDefaultOptions() {
                    return ToolCallingChatOptions.builder().build();
                }
            };
        }
    }

    @Autowired
    private ChatService chat;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void modelProposesThenOnlyTheNextYesBooks() {
        ChatResponse first = chat.handle(SAM, "conv", "Book me the course planning slot");

        assertThat(first.reply()).contains("Reply yes");
        assertThat(first.pendingAction()).isNotNull();
        assertThat(first.pendingAction().type()).isEqualTo("BOOK");
        assertThat(first.pendingAction().summary()).contains("Course Planning").contains("slot 2");
        assertThat(bookedRows()).isZero();

        ChatResponse second = chat.handle(SAM, "conv", "yes");

        assertThat(second.reply()).startsWith("Booked appointment");
        assertThat(second.pendingAction()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT customer_id FROM appointments WHERE slot_id = 2 AND status = 'BOOKED'", Long.class))
                .isEqualTo(6L);
    }

    private int bookedRows() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = 2 AND status = 'BOOKED'", Integer.class);
    }
}
