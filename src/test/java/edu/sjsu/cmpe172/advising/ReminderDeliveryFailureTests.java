package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.ReminderAgent;
import edu.sjsu.cmpe172.advising.ai.ReminderAgent.RunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test config points the gateway at a port nothing listens on, so every delivery fails. With
 * no Gemini key the reminder text is the template. A reminder that was not delivered must not be
 * marked as sent, so the next run tries again.
 */
@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class ReminderDeliveryFailureTests {

    @Autowired
    private ReminderAgent agent;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void undeliveredRemindersAreNotMarkedSentAndAreRetried() {
        assertThat(agent.run(72)).isEqualTo(new RunResult(2, 0, 2));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE reminder_sent_at IS NOT NULL", Integer.class)).isZero();

        assertThat(agent.run(72)).isEqualTo(new RunResult(2, 0, 2));
    }
}
