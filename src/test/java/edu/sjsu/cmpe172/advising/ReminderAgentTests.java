package edu.sjsu.cmpe172.advising;

import com.jayway.jsonpath.JsonPath;
import edu.sjsu.cmpe172.advising.ai.FakeEmbeddingModel;
import edu.sjsu.cmpe172.advising.ai.ReminderAgent;
import edu.sjsu.cmpe172.advising.ai.ReminderAgent.RunResult;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 2 with a stub LLM and the real notification gateway talking to the mock service on this
 * test's random port. Seed data: appointment 1 (Alex, Graduation Check, Maria Chen, ENG 281) and
 * appointment 2 (Jordan, Course Planning, David Nguyen, ENG 285) are BOOKED within the next three
 * days; appointments 3 (cancelled) and 4 (completed, in the past) must never be reminded.
 * <p>
 * The mock service remembers idempotency keys for the life of the context, so only
 * the first test that asserts message text can rely on it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.notification.base-url=")
@AutoConfigureMockMvc
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class ReminderAgentTests {

    private static final int THREE_DAYS = 72;
    private static final String LLM_TEXT = "Hi Alex! Friendly reminder about your advising visit.";
    static final List<String> PROMPTS = new ArrayList<>();

    @TestConfiguration
    static class StubModels {
        @Bean
        EmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }

        @Bean
        ChatModel stubChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    PROMPTS.add(prompt.getContents());
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(LLM_TEXT))));
                }
            };
        }
    }

    @Autowired
    private ReminderAgent agent;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduler;

    @Test
    void llmWrittenReminderIsSentThroughTheNotificationService() throws Exception {
        PROMPTS.clear();

        assertThat(agent.run(THREE_DAYS)).isEqualTo(new RunResult(2, 2, 0));

        assertThat(received("appointment-1-APPOINTMENT_REMINDER", "recipient")).containsExactly("alex.kim@sjsu.edu");
        assertThat(received("appointment-1-APPOINTMENT_REMINDER", "message")).containsExactly(LLM_TEXT);
        assertThat(received("appointment-1-APPOINTMENT_REMINDER", "type")).containsExactly("APPOINTMENT_REMINDER");
        assertThat(received("appointment-2-APPOINTMENT_REMINDER", "recipient")).containsExactly("jordan.lee@sjsu.edu");
        assertThat(received("appointment-3-APPOINTMENT_REMINDER", "recipient")).isEmpty();
        assertThat(received("appointment-4-APPOINTMENT_REMINDER", "recipient")).isEmpty();

        // The LLM was given this appointment's facts and the cancellation policy retrieved from the knowledge base.
        assertThat(PROMPTS).anySatisfy(prompt -> assertThat(prompt)
                .contains("Alex Kim", "Maria Chen", "Graduation Check", "ENG 281", "24 hours"));
        assertThat(remindedAppointmentIds()).containsExactly(1L, 2L);
    }

    @Test
    void anAppointmentIsRemindedOnlyOnce() {
        assertThat(agent.run(THREE_DAYS)).isEqualTo(new RunResult(2, 2, 0));
        assertThat(agent.run(THREE_DAYS)).isEqualTo(new RunResult(0, 0, 0));
    }

    @Test
    void appointmentsOutsideTheWindowAreLeftAlone() {
        assertThat(agent.run(1)).isEqualTo(new RunResult(0, 0, 0));
        assertThat(remindedAppointmentIds()).isEmpty();
    }

    @Test
    void advisorCanTriggerARunOverHttp() throws Exception {
        MockHttpSession advisor = ApiSteps.login(mockMvc, "maria.chen@sjsu.edu");

        mockMvc.perform(post("/api/advisor/agents/reminders/run").param("windowHours", "72").session(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(2))
                .andExpect(jsonPath("$.sent").value(2))
                .andExpect(jsonPath("$.failed").value(0));
        mockMvc.perform(post("/api/advisor/agents/reminders/run").param("windowHours", "0").session(advisor))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentsAndAnonymousUsersCannotTriggerARun() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "sam.rivera@sjsu.edu");

        mockMvc.perform(post("/api/advisor/agents/reminders/run").session(student))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/advisor/agents/reminders/run"))
                .andExpect(status().isUnauthorized());
        assertThat(remindedAppointmentIds()).isEmpty();
    }

    @Test
    void theReminderJobIsScheduledEveryFifteenMinutes() {
        assertThat(scheduler.getScheduledTasks()).anySatisfy(task -> {
            CronTask cron = (CronTask) task.getTask();
            assertThat(cron.getRunnable().toString()).contains("ReminderAgent.runScheduled");
            assertThat(cron.getExpression()).isEqualTo("0 */15 * * * *");
        });
    }

    private List<String> received(String key, String field) throws Exception {
        String json = mockMvc.perform(get("/api/notifications")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[?(@.request.idempotencyKey == '%s')].request.%s".formatted(key, field));
    }

    private List<Long> remindedAppointmentIds() {
        return jdbc.queryForList("SELECT id FROM appointments WHERE reminder_sent_at IS NOT NULL ORDER BY id", Long.class);
    }
}
