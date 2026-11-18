package edu.sjsu.cmpe172.advising.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory pending actions, one per conversation, that expire after ten minutes. */
@Component
public class PendingActionStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingActionStore(Clock clock) {
        this.clock = clock;
    }

    /** Replaces any earlier proposal for the conversation. */
    public void put(String conversationId, PendingAction action) {
        actions.put(conversationId, action);
    }

    /** Looks at the pending action without consuming it. */
    public Optional<PendingAction> find(String conversationId) {
        return Optional.ofNullable(actions.get(conversationId)).filter(this::isFresh);
    }

    /** Removes and returns the pending action, so it can be executed at most once. */
    public Optional<PendingAction> take(String conversationId) {
        return Optional.ofNullable(actions.remove(conversationId)).filter(this::isFresh);
    }

    private boolean isFresh(PendingAction action) {
        return action.createdAt().plus(TTL).isAfter(Instant.now(clock));
    }
}
