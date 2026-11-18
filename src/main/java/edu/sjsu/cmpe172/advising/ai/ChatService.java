package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.dto.ChatHistoryMessage;
import edu.sjsu.cmpe172.advising.dto.ChatResponse;
import edu.sjsu.cmpe172.advising.dto.ChatResponse.PendingSummary;
import edu.sjsu.cmpe172.advising.service.AppointmentWorkflow;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Confirm-before-write, enforced here and not by the prompt. The model can only record a proposal
 * (via {@link AdvisingTools}). This class commits it, through AppointmentWorkflow, when the
 * student's next message is a plain "yes". The check runs before the model is called, so a booking
 * can never be committed in the turn that proposed it.
 */
@Service
public class ChatService {

    private static final Set<String> YES = Set.of("yes", "y", "yeah", "yep", "yup", "sure", "ok", "okay",
            "confirm", "confirmed", "yes please", "please do", "do it", "book it", "go ahead");
    private static final Set<String> NO = Set.of("no", "n", "nope", "nah", "no thanks", "never mind",
            "nevermind", "not now", "stop", "dont", "do not");

    private final AiChat ai;
    private final AdvisingTools tools;
    private final PendingActionStore pendingActions;
    private final AppointmentWorkflow workflow;
    private final Clock clock;
    /** Saved in PostgreSQL through the repository; keeps only the 20 most recent messages per conversation. */
    private final ChatMemory memory;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ChatService(
            AiChat ai,
            AdvisingTools tools,
            PendingActionStore pendingActions,
            AppointmentWorkflow workflow,
            Clock clock,
            ChatMemoryRepository chatMemoryRepository) {
        this.ai = ai;
        this.tools = tools;
        this.pendingActions = pendingActions;
        this.workflow = workflow;
        this.clock = clock;
        this.memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
    }

    /** The saved conversation, oldest first. */
    public List<ChatHistoryMessage> history(String conversationId) {
        return memory.get(conversationId).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> new ChatHistoryMessage(m.getMessageType().name().toLowerCase(), m.getText()))
                .toList();
    }

    public ChatResponse handle(SessionUser user, String conversationId, String message) {
        String answer = normalize(message);
        if (YES.contains(answer) || NO.contains(answer)) {
            Optional<PendingAction> pending = pendingActions.take(conversationId)
                    .filter(action -> action.userId() == user.id());
            if (pending.isPresent()) {
                String reply = YES.contains(answer) ? execute(user, pending.get()) : "Okay, I won't do that.";
                memory.add(conversationId, List.of(new UserMessage(message), new AssistantMessage(reply)));
                return new ChatResponse(reply, null);
            }
        }

        // A proposal can only be confirmed by the very next message. Anything else (a new question, or a
        // "yes" with nothing valid pending) retires it, so a later "yes" can't confirm something stale.
        pendingActions.clear(conversationId);
        String reply;
        try {
            reply = ai.call(() -> ai.client().prompt()
                    .system(systemPrompt(user))
                    .user(message)
                    .tools(tools)
                    .toolContext(Map.of("userId", user.id(), "conversationId", conversationId))
                    .advisors(memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content());
        } catch (RuntimeException ex) {
            // The student never saw a proposal the model recorded just before failing, so it must not stay open.
            pendingActions.clear(conversationId);
            throw ex;
        }
        PendingSummary pending = pendingActions.find(conversationId)
                .map(action -> new PendingSummary(action.type().name(), action.summary()))
                .orElse(null);
        return new ChatResponse(reply == null ? "Sorry, I could not come up with a reply." : reply, pending);
    }

    /** Commits a confirmed action through the transactional workflow. The model plays no part. */
    private String execute(SessionUser user, PendingAction action) {
        try {
            if (action.type() == PendingAction.Type.BOOK) {
                Appointment booked = workflow.book(action.targetId(), user.id(), action.notes());
                return "Booked appointment " + booked.id() + ": " + tools.describeSlot(booked.slotId()) + ".";
            }
            Appointment cancelled = workflow.cancel(action.targetId(), user.id());
            return "Cancelled appointment " + cancelled.id() + ".";
        } catch (SlotConflictException ex) {
            return "Sorry, that slot was just taken. " + tools.alternativesFor(action.targetId());
        } catch (ResourceNotFoundException | ForbiddenException | BadRequestException ex) {
            return "Sorry, I could not do that: " + ex.getMessage();
        }
    }

    private String systemPrompt(SessionUser user) {
        LocalDate today = LocalDate.now(clock);
        return """
                You are the scheduling assistant for a university academic advising office. \
                You are chatting with %s, a student.
                Today is %s %s (time zone %s). Work out dates such as "this week" or "tomorrow" from that.
                %s
                Rules:
                - Look things up with findAvailableSlots and listMyAppointments. Never invent slots or ids.
                - You cannot book or cancel. To book call requestBooking, to cancel call requestCancellation, \
                then show the student the summary and ask them to reply yes to confirm. \
                Never say a booking or cancellation is done.
                - If a slot is taken, offer the alternatives the tool returns.
                - For questions about hours, locations, services or policies, call searchPolicies and answer \
                only from what it returns. If it does not cover the question, say you don't know.
                - Keep replies short and friendly.
                """.formatted(user.fullName(), today.getDayOfWeek(), today, clock.getZone(), tools.catalog());
    }

    /** "Yes!" and "yes," both become "yes". */
    private static String normalize(String message) {
        return message.toLowerCase().replaceAll("[^a-z ]", "").trim();
    }
}
