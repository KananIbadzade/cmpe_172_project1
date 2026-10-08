package edu.sjsu.cmpe172.advising.dto;

import java.util.List;

public record SlotPageResponse(
        List<SlotResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
