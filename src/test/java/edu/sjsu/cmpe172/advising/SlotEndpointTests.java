package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SlotEndpointTests {

    private static final int SEEDED_AVAILABLE_SLOTS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeReturnsSystemStatus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.system").value("advising-scheduler"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.environment").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.milestone").value("milestone-1"));
    }

    @Test
    void listSlotsReturnsOnlyAvailableSlotsAsDtos() throws Exception {
        mockMvc.perform(get("/api/slots"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(SEEDED_AVAILABLE_SLOTS)))
                .andExpect(jsonPath("$[*].booked", everyItem(is(false))))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].providerId").isNumber())
                .andExpect(jsonPath("$[0].serviceId").isNumber())
                .andExpect(jsonPath("$[0].startTime").isString())
                .andExpect(jsonPath("$[0].endTime").isString())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    void getSlotReturnsBookedSlot() throws Exception {
        mockMvc.perform(get("/api/slots/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.booked").value(true));
    }

    @Test
    void getMissingSlotReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/slots/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Slot 999999 not found"));
    }
}
