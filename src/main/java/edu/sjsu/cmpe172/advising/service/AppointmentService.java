package edu.sjsu.cmpe172.advising.service;

import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.domain.AppointmentStatus;
import edu.sjsu.cmpe172.advising.domain.AvailabilitySlot;
import edu.sjsu.cmpe172.advising.repository.AppointmentJdbcRepository;
import edu.sjsu.cmpe172.advising.repository.SlotJdbcRepository;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentJdbcRepository appointmentRepository;
    private final SlotJdbcRepository slotRepository;

    public AppointmentService(
            AppointmentJdbcRepository appointmentRepository,
            SlotJdbcRepository slotRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
    }

    public List<Appointment> listForCustomer(long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }

    /**
     * Book a free slot for a student. Locks the slot row so two concurrent bookers
     * cannot both succeed; the partial unique index is the final backstop.
     */
    @Transactional
    public Appointment book(long slotId, long customerId, String notes) {
        AvailabilitySlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId));

        if (slot.booked()) {
            throw new SlotConflictException("Slot " + slotId + " is already booked");
        }

        try {
            long appointmentId = appointmentRepository.insert(
                    customerId,
                    slot.providerId(),
                    slot.id(),
                    slot.serviceId(),
                    notes);
            slotRepository.setBooked(slot.id(), true);
            return appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new IllegalStateException("Booked appointment not found"));
        } catch (DuplicateKeyException ex) {
            throw new SlotConflictException("Slot " + slotId + " is already booked");
        }
    }

    /**
     * Cancel a BOOKED appointment. Only the owning student may cancel.
     */
    @Transactional
    public Appointment cancel(long appointmentId, long customerId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        if (appointment.customerId() != customerId) {
            throw new ForbiddenException("Only the appointment owner can cancel");
        }
        if (!AppointmentStatus.BOOKED.name().equals(appointment.status())) {
            throw new BadRequestException("Only BOOKED appointments can be cancelled");
        }

        // Lock the slot so cancel and a racing book serialize cleanly.
        slotRepository.findByIdForUpdate(appointment.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot", appointment.slotId()));

        appointmentRepository.updateStatus(appointmentId, AppointmentStatus.CANCELLED);
        slotRepository.setBooked(appointment.slotId(), false);

        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalStateException("Cancelled appointment not found"));
    }
}
