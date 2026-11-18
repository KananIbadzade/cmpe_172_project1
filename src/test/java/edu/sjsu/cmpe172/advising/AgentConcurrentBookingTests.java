package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.ai.AdvisingTools;
import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required M4 test: the agent's book path goes through the transactional service, so double-booking
 * is still blocked when the agent books. Two students each get a pending BOOK for the same free slot
 * and confirm at the same moment, both through ChatService. Exactly one may succeed.
 */
@SpringBootTest
@Sql({"/reset-data.sql", "/db/migration/V2__seed_demo_data.sql"})
class AgentConcurrentBookingTests {

    private static final SessionUser ALEX = new SessionUser(4, "alex.kim@sjsu.edu", "Alex Kim", UserRole.STUDENT);
    private static final SessionUser JORDAN = new SessionUser(5, "jordan.lee@sjsu.edu", "Jordan Lee", UserRole.STUDENT);
    private static final long FREE_SLOT = 2;

    @Autowired
    private AdvisingTools tools;
    @Autowired
    private ChatService chat;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoStudentsConfirmingTheSameSlotAtOnceProduceOneBooking() throws Exception {
        tools.requestBooking(FREE_SLOT, null, ctx(ALEX, "alex"));
        tools.requestBooking(FREE_SLOT, null, ctx(JORDAN, "jordan"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> replies;
        try {
            Future<String> alex = pool.submit(confirm(ALEX, "alex", ready, start));
            Future<String> jordan = pool.submit(confirm(JORDAN, "jordan", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            replies = List.of(alex.get(10, TimeUnit.SECONDS), jordan.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(replies).filteredOn(r -> r.startsWith("Booked appointment")).hasSize(1);
        assertThat(replies).filteredOn(r -> r.contains("just taken") && r.contains("Other free slots")).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class, FREE_SLOT)).isEqualTo(1);
    }

    private Callable<String> confirm(SessionUser user, String conversationId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return chat.handle(user, conversationId, "yes").reply();
        };
    }

    private static ToolContext ctx(SessionUser user, String conversationId) {
        return new ToolContext(Map.of("userId", user.id(), "conversationId", conversationId));
    }
}
