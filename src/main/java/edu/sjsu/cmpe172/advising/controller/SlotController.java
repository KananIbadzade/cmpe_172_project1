package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.dto.SlotPageResponse;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public SlotPageResponse listAvailableSlots(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return slotService.listAvailableSlots(providerId, serviceId, date, page, size);
    }

    @GetMapping("/{id}")
    public SlotResponse getSlot(@PathVariable long id) {
        return slotService.getSlot(id);
    }
}
