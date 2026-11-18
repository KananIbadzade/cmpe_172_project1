package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.AdvisingTools;
import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.ai.PendingActionStore;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 1 without the LLM: the tools only propose, and ChatService commits a confirmed proposal
 * through AppointmentWorkflow. Seed data: students Alex=4 and Jordan=5 already hold slots 1 and 5,
 * Sam=6 has none; slot 2 (Course Planning) and slot 3 (Graduation Check) are free.
 */
@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AgentBookingFlowTests {

    private static final SessionUser ALEX = new SessionUser(4, "alex.kim@sjsu.edu", "Alex Kim", UserRole.STUDENT);
    private static final SessionUser SAM = new SessionUser(6, "sam.rivera@sjsu.edu", "Sam Rivera", UserRole.STUDENT);
    private static final long FREE_SLOT = 2;

    @Autowired
    private AdvisingTools tools;
    @Autowired
    private ChatService chat;
    @Autowired
    private PendingActionStore pendingActions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void toolsAcceptTheJsonArgumentsTheModelSends() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);

        String slots = call(callbacks, "findAvailableSlots", "{\"fromDate\":\"2000-01-01\",\"serviceId\":4}", ctx(SAM, "s"));
        assertThat(slots).contains("Course Planning").contains("Maria Chen");

        String proposal = call(callbacks, "requestBooking", "{\"slotId\":" + FREE_SLOT + "}", ctx(SAM, "s"));
        assertThat(proposal).contains("Nothing is booked yet");
        assertThat(pendingActions.find("s")).isPresent();
    }

    @Test
    void requestBookingWritesNothing() {
        tools.requestBooking(FREE_SLOT, "bring transcript", ctx(SAM, "s"));

        assertThat(appointmentRows(FREE_SLOT)).isZero();
        assertThat(pendingActions.find("s")).isPresent();
    }

    @Test
    void yesCommitsTheBookingThroughTheWorkflow() {
        tools.requestBooking(FREE_SLOT, "bring transcript", ctx(SAM, "s"));

        String reply = chat.handle(SAM, "s", "Yes!").reply();

        assertThat(reply).startsWith("Booked appointment");
        assertThat(jdbc.queryForObject(
                "SELECT customer_id FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Long.class, FREE_SLOT)).isEqualTo(6L);
        assertThat(pendingActions.find("s")).isEmpty();
    }

    @Test
    void secondStudentConfirmingTheSameSlotGetsAlternatives() {
        tools.requestBooking(FREE_SLOT, null, ctx(SAM, "sam"));
        tools.requestBooking(FREE_SLOT, null, ctx(ALEX, "alex"));

        assertThat(chat.handle(SAM, "sam", "yes").reply()).startsWith("Booked appointment");
        String reply = chat.handle(ALEX, "alex", "yes").reply();

        assertThat(reply).contains("just taken").contains("Other free slots");
        assertThat(appointmentRows(FREE_SLOT)).isEqualTo(1);
    }

    @Test
    void noClearsTheProposalWithoutBooking() {
        tools.requestBooking(FREE_SLOT, null, ctx(SAM, "s"));

        assertThat(chat.handle(SAM, "s", "no").reply()).startsWith("Okay");

        assertThat(pendingActions.find("s")).isEmpty();
        assertThat(appointmentRows(FREE_SLOT)).isZero();
    }

    @Test
    void cancellationNeedsConfirmationAndOwnership() {
        assertThat(tools.requestCancellation(1, ctx(SAM, "sam"))).contains("no booked appointment");
        assertThat(pendingActions.find("sam")).isEmpty();

        tools.requestCancellation(1, ctx(ALEX, "alex"));
        assertThat(status(1)).isEqualTo("BOOKED");
        assertThat(chat.handle(ALEX, "alex", "yes").reply()).startsWith("Cancelled appointment 1");
        assertThat(status(1)).isEqualTo("CANCELLED");
    }

    private static ToolContext ctx(SessionUser user, String conversationId) {
        return new ToolContext(Map.of("userId", user.id(), "conversationId", conversationId));
    }

    private static String call(ToolCallback[] callbacks, String name, String json, ToolContext context) {
        return Arrays.stream(callbacks)
                .filter(c -> c.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow()
                .call(json, context);
    }

    private int appointmentRows(long slotId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
    }

    private String status(long appointmentId) {
        return jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, appointmentId);
    }
}
