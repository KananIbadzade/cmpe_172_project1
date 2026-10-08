package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.dto.SlotPageResponse;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.repository.ServiceJdbcRepository;
import edu.sjsu.cmpe172.advising.repository.SlotJdbcRepository;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SlotService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final SlotJdbcRepository slotRepository;
    private final ServiceJdbcRepository serviceRepository;

    public SlotService(SlotJdbcRepository slotRepository, ServiceJdbcRepository serviceRepository) {
        this.slotRepository = slotRepository;
        this.serviceRepository = serviceRepository;
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

    public List<AvailabilitySlot> listForProvider(long providerId) {
        return slotRepository.findByProviderId(providerId);
    }

    public AvailabilitySlot createSlot(
            long providerId, long serviceId, OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("end time must be after start time");
        }
        serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));
        try {
            long id = slotRepository.insert(providerId, serviceId, start, end);
            return slotRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Created slot not found"));
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("You already have a slot starting at that time");
        }
    }

    public void deleteUnbookedSlot(long slotId, long providerId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId));
        if (slot.providerId() != providerId) {
            throw new ForbiddenException("You can only remove your own slots");
        }
        if (slot.booked()) {
            throw new BadRequestException("Booked slots cannot be removed");
        }
        if (!slotRepository.deleteUnbooked(slotId, providerId)) {
            throw new BadRequestException("Slot could not be removed");
        }
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
