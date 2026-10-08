package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.dto.SlotPageResponse;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.repository.SlotJdbcRepository;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SlotService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final SlotJdbcRepository slotRepository;

    public SlotService(SlotJdbcRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public SlotPageResponse listAvailableSlots(
            Long providerId, Long serviceId, LocalDate date, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        long totalItems = slotRepository.countAvailable(providerId, serviceId, date);
        int offset = pageNumber * pageSize;
        List<SlotResponse> items = slotRepository
                .findAvailable(providerId, serviceId, date, pageSize, offset)
                .stream()
                .map(SlotService::toResponse)
                .toList();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageSize);
        return new SlotPageResponse(items, pageNumber, pageSize, totalItems, totalPages);
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
