package edu.sjsu.cmpe172.advising;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.service.AppointmentService;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AppointmentServiceTests {

    private static final long ALEX_ID = 4L;
    private static final long JORDAN_ID = 5L;

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void bookCreatesBookedAppointment() {
        Appointment booked = appointmentService.book(2L, ALEX_ID, "Service-level book");
        assertThat(booked.status()).isEqualTo("BOOKED");
        assertThat(booked.slotId()).isEqualTo(2L);
        assertThat(booked.customerId()).isEqualTo(ALEX_ID);
    }

    @Test
    void bookRejectsAlreadyTakenSlot() {
        assertThatThrownBy(() -> appointmentService.book(1L, JORDAN_ID, "Nope"))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void cancelIsOwnerOnly() {
        assertThatThrownBy(() -> appointmentService.cancel(1L, JORDAN_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner");

        Appointment cancelled = appointmentService.cancel(1L, ALEX_ID);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }
}
