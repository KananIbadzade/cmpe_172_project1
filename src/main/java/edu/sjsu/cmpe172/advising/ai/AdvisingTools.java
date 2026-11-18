package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.domain.AppointmentStatus;
import edu.sjsu.cmpe172.advising.dto.SlotPageResponse;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.repository.ProviderJdbcRepository;
import edu.sjsu.cmpe172.advising.repository.ProviderJdbcRepository.ProviderOption;
import edu.sjsu.cmpe172.advising.repository.ServiceJdbcRepository;
import edu.sjsu.cmpe172.advising.service.AppointmentService;
import edu.sjsu.cmpe172.advising.service.SlotService;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tools the chat model can call. They only read, or record a proposal in {@link PendingActionStore}.
 * Nothing here writes to the database: bookings and cancellations are committed by
 * {@link ChatService} through AppointmentWorkflow, after the student confirms.
 * The student's id comes from the ToolContext (set from the session), never from the model.
 */
@Component
public class AdvisingTools {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm");
    private static final int SEARCH_LIMIT = 10;
    private static final int ALTERNATIVES_LIMIT = 3;
    private static final int POLICY_CHUNKS = 3;

    public record SlotInfo(long slotId, String advisor, String service, String start) {
        String describe() {
            return "%s with %s on %s (slot %d)".formatted(service, advisor, start, slotId);
        }
    }

    public record AppointmentInfo(long appointmentId, String advisor, String service, String start) {}

    private final SlotService slotService;
    private final AppointmentService appointmentService;
    private final ProviderJdbcRepository providerRepository;
    private final ServiceJdbcRepository serviceRepository;
    private final PendingActionStore pendingActions;
    private final KnowledgeBase knowledge;
    private final Clock clock;

    public AdvisingTools(
            SlotService slotService,
            AppointmentService appointmentService,
            ProviderJdbcRepository providerRepository,
            ServiceJdbcRepository serviceRepository,
            PendingActionStore pendingActions,
            KnowledgeBase knowledge,
            Clock clock) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.pendingActions = pendingActions;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    @Tool(description = "Find free advising slots that have not started yet, earliest first (at most 10). "
            + "All filters are optional. Dates are YYYY-MM-DD and inclusive.")
    public List<SlotInfo> findAvailableSlots(
            @ToolParam(required = false, description = "Earliest day, YYYY-MM-DD") LocalDate fromDate,
            @ToolParam(required = false, description = "Latest day, YYYY-MM-DD") LocalDate toDate,
            @ToolParam(required = false, description = "Service id") Long serviceId,
            @ToolParam(required = false, description = "Advisor id") Long providerId) {
        return upcomingSlots(fromDate, toDate, serviceId, providerId, SEARCH_LIMIT);
    }

    @Tool(description = "List the current student's booked appointments.")
    public List<AppointmentInfo> listMyAppointments(ToolContext context) {
        long userId = userId(context);
        List<AppointmentInfo> result = new ArrayList<>();
        for (Appointment appointment : appointmentService.listForCustomer(userId)) {
            if (AppointmentStatus.BOOKED.name().equals(appointment.status())) {
                SlotInfo slot = slotInfo(slotService.getSlot(appointment.slotId()));
                result.add(new AppointmentInfo(appointment.id(), slot.advisor(), slot.service(), slot.start()));
            }
        }
        return result;
    }

    @Tool(description = "Propose booking a slot for the current student. This does NOT book anything: "
            + "it saves the proposal, and the student must reply yes in their next message. "
            + "Show the student the summary and ask them to confirm.")
    public String requestBooking(
            @ToolParam(description = "Slot id from findAvailableSlots") long slotId,
            @ToolParam(required = false, description = "Optional note for the advisor") String notes,
            ToolContext context) {
        SlotResponse slot;
        try {
            slot = slotService.getSlot(slotId);
        } catch (ResourceNotFoundException ex) {
            return "There is no slot with id " + slotId + ".";
        }
        if (slot.booked()) {
            return "Slot " + slotId + " is already taken. " + alternativesFor(slotId);
        }
        if (slot.startTime().isBefore(OffsetDateTime.now(clock))) {
            return "Slot " + slotId + " has already started, so it cannot be booked.";
        }
        String summary = "Book " + slotInfo(slot).describe();
        pendingActions.put(conversationId(context), new PendingAction(
                PendingAction.Type.BOOK, slotId, notes, summary, userId(context), Instant.now(clock)));
        return "Nothing is booked yet. Proposal: " + summary
                + ". Ask the student to reply yes to confirm or no to cancel.";
    }

    @Tool(description = "Propose cancelling one of the current student's booked appointments. This does NOT "
            + "cancel anything: the student must reply yes in their next message. "
            + "Show the student the summary and ask them to confirm.")
    public String requestCancellation(
            @ToolParam(description = "Appointment id from listMyAppointments") long appointmentId,
            ToolContext context) {
        long userId = userId(context);
        Appointment appointment = appointmentService.listForCustomer(userId).stream()
                .filter(a -> a.id() == appointmentId)
                .filter(a -> AppointmentStatus.BOOKED.name().equals(a.status()))
                .findFirst()
                .orElse(null);
        if (appointment == null) {
            return "The student has no booked appointment with id " + appointmentId + ".";
        }
        String summary = "Cancel appointment " + appointmentId + ": "
                + slotInfo(slotService.getSlot(appointment.slotId())).describe();
        pendingActions.put(conversationId(context), new PendingAction(
                PendingAction.Type.CANCEL, appointmentId, null, summary, userId, Instant.now(clock)));
        return "Nothing is cancelled yet. Proposal: " + summary
                + ". Ask the student to reply yes to confirm or no to keep it.";
    }

    @Tool(description = "Look up the advising office's policies and FAQ: office hours, locations, services, "
            + "booking rules, cancellation and no-show policy, what to bring, reminders. "
            + "Answer policy questions only from what this returns.")
    public String searchPolicies(@ToolParam(description = "The student's question") String question) {
        List<Document> chunks = knowledge.search(question, POLICY_CHUNKS);
        if (chunks.isEmpty()) {
            return "No policy information found.";
        }
        return chunks.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
    }

    /**
     * Free slots to offer instead of the given one, as text for the model or the student:
     * the same service if any are free, otherwise the earliest slots of any service.
     */
    public String alternativesFor(long slotId) {
        List<SlotInfo> options = upcomingSlots(null, null, slotService.getSlot(slotId).serviceId(), null,
                ALTERNATIVES_LIMIT);
        if (options.isEmpty()) {
            options = upcomingSlots(null, null, null, null, ALTERNATIVES_LIMIT);
        }
        if (options.isEmpty()) {
            return "There are no free slots right now.";
        }
        return "Other free slots: " + options.stream().map(SlotInfo::describe).collect(Collectors.joining("; "));
    }

    public String describeSlot(long slotId) {
        return slotInfo(slotService.getSlot(slotId)).describe();
    }

    /** Advisor and service ids and names, for the system prompt, so the model can filter by id. */
    public String catalog() {
        String advisors = providerRepository.findOptions().stream()
                .map(p -> p.id() + " = " + p.fullName())
                .collect(Collectors.joining(", "));
        String services = serviceRepository.findAll().stream()
                .map(s -> s.id() + " = " + s.name())
                .collect(Collectors.joining(", "));
        return "Advisors (id = name): " + advisors + "\nServices (id = name): " + services;
    }

    /** Free slots that have not started, earliest first. SlotService does not exclude past slots. */
    private List<SlotInfo> upcomingSlots(
            LocalDate from, LocalDate to, Long serviceId, Long providerId, int limit) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SlotInfo> result = new ArrayList<>();
        for (int page = 0; ; page++) {
            SlotPageResponse slots = slotService.listAvailableSlots(providerId, serviceId, null, page, 50);
            for (SlotResponse slot : slots.items()) {
                LocalDate day = slot.startTime().atZoneSameInstant(clock.getZone()).toLocalDate();
                if (slot.startTime().isBefore(now) || (from != null && day.isBefore(from))) {
                    continue;
                }
                if (to != null && day.isAfter(to)) {
                    return result; // slots are sorted by start time, so nothing later can match
                }
                result.add(slotInfo(slot));
                if (result.size() == limit) {
                    return result;
                }
            }
            if (page + 1 >= slots.totalPages()) {
                return result;
            }
        }
    }

    private SlotInfo slotInfo(SlotResponse slot) {
        Map<Long, String> advisors = providerRepository.findOptions().stream()
                .collect(Collectors.toMap(ProviderOption::id, ProviderOption::fullName));
        String service = serviceRepository.findById(slot.serviceId()).map(s -> s.name()).orElse("Advising");
        return new SlotInfo(
                slot.id(),
                advisors.getOrDefault(slot.providerId(), "an advisor"),
                service,
                TIME.format(slot.startTime().atZoneSameInstant(clock.getZone())));
    }

    private static long userId(ToolContext context) {
        return (Long) context.getContext().get("userId");
    }

    private static String conversationId(ToolContext context) {
        return (String) context.getContext().get("conversationId");
    }
}
