package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<SlotResponse> listAvailableSlots() {
        return slotService.listAvailableSlots();
    }

    @GetMapping("/{id}")
    public SlotResponse getSlot(@PathVariable long id) {
        return slotService.getSlot(id);
    }
}
