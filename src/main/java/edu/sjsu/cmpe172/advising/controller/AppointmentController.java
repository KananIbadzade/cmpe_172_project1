package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.dto.AppointmentResponse;
import edu.sjsu.cmpe172.advising.dto.BookRequest;
import edu.sjsu.cmpe172.advising.service.AppointmentService;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentResponse> myAppointments(HttpServletRequest request) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        return appointmentService.listForCustomer(user.id()).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@RequestBody BookRequest body, HttpServletRequest request) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        if (body == null || body.slotId() == null) {
            throw new BadRequestException("slotId is required");
        }
        return AppointmentResponse.from(
                appointmentService.book(body.slotId(), user.id(), body.notes()));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable long id, HttpServletRequest request) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        return AppointmentResponse.from(appointmentService.cancel(id, user.id()));
    }
}
