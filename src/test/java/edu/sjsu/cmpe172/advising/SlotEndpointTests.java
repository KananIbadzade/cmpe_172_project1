package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
        mockMvc.perform(get("/api/slots").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(SEEDED_AVAILABLE_SLOTS)))
                .andExpect(jsonPath("$.totalItems").value(SEEDED_AVAILABLE_SLOTS))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[*].booked", everyItem(is(false))))
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].providerId").isNumber())
                .andExpect(jsonPath("$.items[0].serviceId").isNumber())
                .andExpect(jsonPath("$.items[0].startTime").isString())
                .andExpect(jsonPath("$.items[0].endTime").isString())
                .andExpect(jsonPath("$.items[0].createdAt").doesNotExist());
    }

    @Test
    void listSlotsFiltersByProvider() throws Exception {
        mockMvc.perform(get("/api/slots").param("providerId", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].providerId", everyItem(is(1))));
    }

    @Test
    void listSlotsFiltersByService() throws Exception {
        mockMvc.perform(get("/api/slots").param("serviceId", "2").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].serviceId", everyItem(is(2))));
    }

    @Test
    void listSlotsFiltersByDate() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        mockMvc.perform(get("/api/slots").param("date", tomorrow).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void listSlotsPaginatesWithLimitAndOffset() throws Exception {
        mockMvc.perform(get("/api/slots").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(SEEDED_AVAILABLE_SLOTS))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/slots").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void listSlotsRejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/slots").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
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
