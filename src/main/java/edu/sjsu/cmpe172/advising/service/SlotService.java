package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.repository.SlotJdbcRepository;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlotService {

    private final SlotJdbcRepository slotRepository;

    public SlotService(SlotJdbcRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotResponse> listAvailableSlots() {
        return slotRepository.findAvailable().stream()
                .map(SlotService::toResponse)
                .toList();
    }

    public SlotResponse getSlot(long id) {
        return slotRepository.findById(id)
                .map(SlotService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", id));
    }

    private static SlotResponse toResponse(AvailabilitySlot slot) {
        return new SlotResponse(
                slot.id(),
                slot.providerId(),
                slot.serviceId(),
                slot.startTime(),
                slot.endTime(),
                slot.booked()
        );
    }
}
