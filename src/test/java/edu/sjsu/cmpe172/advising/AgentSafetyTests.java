package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.AdvisingTools;
import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.ai.PendingAction;
import edu.sjsu.cmpe172.advising.ai.PendingActionStore;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.service.exception.AiNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A "yes" may only commit a live proposal made by the same student. In every other case it is not
 * treated as a confirmation and is handed to the model; these tests run with no AI configured, so
 * that shows up as AiNotConfiguredException, and in every case no appointment row is written.
 */
@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AgentSafetyTests {

    private static final SessionUser SAM = new SessionUser(6, "sam.rivera@sjsu.edu", "Sam Rivera", UserRole.STUDENT);
    private static final long FREE_SLOT = 2;
    private static final int SEEDED_APPOINTMENTS = 4;

    /** A clock the test can move forward, to expire pending actions without waiting. */
    static class MutableClock extends Clock {
        private Instant now = Instant.now();

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private AdvisingTools tools;
    @Autowired
    private ChatService chat;
    @Autowired
    private PendingActionStore pendingActions;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void yesWithNothingPendingBooksNothing() {
        assertThatThrownBy(() -> chat.handle(SAM, "none", "yes")).isInstanceOf(AiNotConfiguredException.class);

        assertThat(appointmentCount()).isEqualTo(SEEDED_APPOINTMENTS);
    }

    @Test
    void expiredProposalIsNotCommitted() {
        tools.requestBooking(FREE_SLOT, null, ctx("expired"));
        clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> chat.handle(SAM, "expired", "yes")).isInstanceOf(AiNotConfiguredException.class);

        assertThat(appointmentCount()).isEqualTo(SEEDED_APPOINTMENTS);
    }

    @Test
    void proposalStillCommitsJustBeforeItExpires() {
        tools.requestBooking(FREE_SLOT, null, ctx("fresh"));
        clock.advance(Duration.ofMinutes(9));

        assertThat(chat.handle(SAM, "fresh", "yes").reply()).startsWith("Booked appointment");
    }

    @Test
    void proposalMadeForAnotherStudentIsNotCommitted() {
        // Alex (id 4) has a pending booking; Sam is somehow in the same conversation and says yes.
        pendingActions.put("shared", new PendingAction(
                PendingAction.Type.BOOK, FREE_SLOT, null, "Book slot 2", 4, Instant.now(clock)));

        assertThatThrownBy(() -> chat.handle(SAM, "shared", "yes")).isInstanceOf(AiNotConfiguredException.class);

        assertThat(appointmentCount()).isEqualTo(SEEDED_APPOINTMENTS);
    }

    @Test
    void proposalTakesTheStudentFromTheSessionContextNotFromTheModel() {
        tools.requestBooking(FREE_SLOT, null, ctx("identity"));

        assertThat(pendingActions.find("identity")).get()
                .extracting(PendingAction::userId).isEqualTo(6L);
    }

    private static ToolContext ctx(String conversationId) {
        return new ToolContext(Map.of("userId", SAM.id(), "conversationId", conversationId));
    }

    private int appointmentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class);
    }
}
