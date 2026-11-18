package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.AdvisingTools;
import edu.sjsu.cmpe172.advising.ai.AiChat;
import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.ai.PendingActionStore;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.service.AppointmentWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved chat history: conversations live in PostgreSQL (one conversation per student), so a new
 * ChatService instance, a new login session, or a server restart all see the same history.
 * The chat model is a stub that answers "Noted." and records every prompt it receives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class ChatHistoryTests {

    private static final SessionUser SAM = new SessionUser(6, "sam.rivera@sjsu.edu", "Sam Rivera", UserRole.STUDENT);
    private static final SessionUser ALEX = new SessionUser(4, "alex.kim@sjsu.edu", "Alex Kim", UserRole.STUDENT);
    static final List<String> PROMPTS = new ArrayList<>();

    @TestConfiguration
    static class StubModel {
        @Bean
        ChatModel notedChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    PROMPTS.add(prompt.getContents());
                    return new ChatResponse(List.of(new Generation(new AssistantMessage("Noted."))));
                }
            };
        }
    }

    @Autowired
    private ChatService chat;
    @Autowired
    private AiChat ai;
    @Autowired
    private AdvisingTools tools;
    @Autowired
    private PendingActionStore pendingActions;
    @Autowired
    private AppointmentWorkflow workflow;
    @Autowired
    private Clock clock;
    @Autowired
    private ChatMemoryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearPrompts() {
        PROMPTS.clear();
    }

    @Test
    void aNewChatServiceStillSeesEarlierMessages() {
        chat.handle(SAM, "student-6", "My favourite number is 42");

        // A second instance has no state of its own, like the app after a restart.
        ChatService restarted = new ChatService(ai, tools, pendingActions, workflow, clock, repository);
        restarted.handle(SAM, "student-6", "What did I just say?");

        assertThat(PROMPTS).hasSize(2);
        assertThat(PROMPTS.get(1)).contains("My favourite number is 42").contains("Noted.");
    }

    @Test
    void messagesAreStoredInPostgresPerStudent() {
        chat.handle(SAM, "student-6", "hello from sam");

        assertThat(storedMessages("student-6")).isEqualTo(2); // the question and the answer
        assertThat(storedMessages("student-4")).isZero();
    }

    @Test
    void oneStudentNeverSeesAnotherStudentsMessages() {
        chat.handle(SAM, "student-6", "sam's private note");
        chat.handle(ALEX, "student-4", "alex asks something");

        assertThat(PROMPTS.get(1)).doesNotContain("sam's private note");
    }

    @Test
    void historyIsCappedAtTwentyMessages() {
        for (int i = 1; i <= 15; i++) {
            chat.handle(SAM, "student-6", "message " + i);
        }

        assertThat(storedMessages("student-6")).isEqualTo(20);
        assertThat(PROMPTS.get(14)).contains("message 14").doesNotContain("message 1 "); // window is sliding
    }

    @Test
    void aConfirmedBookingTurnIsSavedToo() {
        tools.requestBooking(2, null, new ToolContext(Map.of("userId", 6L, "conversationId", "student-6")));

        chat.handle(SAM, "student-6", "yes");

        assertThat(jdbc.queryForList(
                "SELECT content FROM spring_ai_chat_memory WHERE conversation_id = 'student-6' AND type = 'ASSISTANT'",
                String.class)).singleElement().asString().startsWith("Booked appointment");
    }

    @Test
    void historyFollowsTheStudentAcrossLoginSessions() throws Exception {
        MockHttpSession firstLogin = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");
        mockMvc.perform(post("/api/student/chat")
                        .session(firstLogin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Remember that I like mornings\"}"))
                .andExpect(status().isOk());

        MockHttpSession secondLogin = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");
        mockMvc.perform(get("/api/student/chat/history").session(secondLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value("Remember that I like mornings"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].text").value("Noted."));

        MockHttpSession alex = ApiSteps.login(mockMvc, "alex.kim@sjsu.edu");
        mockMvc.perform(get("/api/student/chat/history").session(alex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historyIsStudentOnly() throws Exception {
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(get("/api/student/chat/history").session(advisor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/student/chat/history")).andExpect(status().isUnauthorized());
    }

    private int storedMessages(String conversationId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM spring_ai_chat_memory WHERE conversation_id = ?", Integer.class, conversationId);
    }
}
