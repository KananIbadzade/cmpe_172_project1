package edu.sjsu.cmpe172.advising.controller.ui;

import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.Appointment;
import edu.sjsu.cmpe172.advising.domain.Provider;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.dto.SlotPageResponse;
import edu.sjsu.cmpe172.advising.dto.SlotResponse;
import edu.sjsu.cmpe172.advising.repository.ProviderJdbcRepository;
import edu.sjsu.cmpe172.advising.repository.ServiceJdbcRepository;
import edu.sjsu.cmpe172.advising.service.AppointmentService;
import edu.sjsu.cmpe172.advising.service.AuthService;
import edu.sjsu.cmpe172.advising.service.SlotService;
import edu.sjsu.cmpe172.advising.service.exception.InvalidCredentialsException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
public class UiPageController {

    private final AuthService authService;
    private final SlotService slotService;
    private final AppointmentService appointmentService;
    private final ProviderJdbcRepository providerRepository;
    private final ServiceJdbcRepository serviceRepository;

    public UiPageController(
            AuthService authService,
            SlotService slotService,
            AppointmentService appointmentService,
            ProviderJdbcRepository providerRepository,
            ServiceJdbcRepository serviceRepository) {
        this.authService = authService;
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/")
    public String index(HttpServletRequest request, Model model) {
        model.addAttribute("user", SessionAuth.getUserOrNull(request.getSession(false)));
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            Model model) {
        SessionUser user = SessionAuth.getUserOrNull(request.getSession(false));
        if (user != null) {
            return redirectForRole(user);
        }
        model.addAttribute("error", error);
        model.addAttribute("user", null);
        return "login";
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String loginForm(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            SessionUser user = authService.authenticate(email, password);
            HttpSession session = request.getSession(true);
            SessionAuth.setUser(session, user);
            return redirectForRole(user);
        } catch (InvalidCredentialsException ex) {
            redirectAttributes.addAttribute("error", "Invalid email or password");
            return "redirect:/login";
        }
    }

    @PostMapping(path = "/logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String logoutForm(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            SessionAuth.clear(session);
        }
        return "redirect:/";
    }

    @GetMapping("/slots")
    public String slots(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            Model model) {
        SlotPageResponse result = slotService.listAvailableSlots(providerId, serviceId, date, page, 5);
        model.addAttribute("user", SessionAuth.getUserOrNull(request.getSession(false)));
        model.addAttribute("page", result);
        model.addAttribute("providers", providerRepository.findOptions());
        model.addAttribute("services", serviceRepository.findAll());
        model.addAttribute("providerId", providerId);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("date", date);
        model.addAttribute("error", error);
        return "slots";
    }

    @GetMapping("/book/{slotId}")
    public String bookForm(@PathVariable long slotId, HttpServletRequest request, Model model) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        SlotResponse slot = slotService.getSlot(slotId);
        if (slot.booked()) {
            return "redirect:/slots?error=taken";
        }
        model.addAttribute("user", user);
        model.addAttribute("slot", slot);
        model.addAttribute("providerName", providerName(slot.providerId()));
        model.addAttribute("serviceName", serviceName(slot.serviceId()));
        return "book";
    }

    @PostMapping("/book/{slotId}")
    public String bookSubmit(
            @PathVariable long slotId,
            @RequestParam(required = false) String notes,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        try {
            Appointment appointment = appointmentService.book(slotId, user.id(), notes);
            redirectAttributes.addAttribute("id", appointment.id());
            return "redirect:/confirmation";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/slots";
        }
    }

    @GetMapping("/confirmation")
    public String confirmation(
            @RequestParam long id,
            HttpServletRequest request,
            Model model) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        Appointment appointment = appointmentService.listForCustomer(user.id()).stream()
                .filter(a -> a.id() == id)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        model.addAttribute("user", user);
        model.addAttribute("appointment", appointment);
        model.addAttribute("providerName", providerName(appointment.providerId()));
        model.addAttribute("serviceName", serviceName(appointment.serviceId()));
        return "confirmation";
    }

    @GetMapping("/my-appointments")
    public String myAppointments(HttpServletRequest request, Model model) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        List<Appointment> appointments = appointmentService.listForCustomer(user.id());
        model.addAttribute("user", user);
        model.addAttribute("appointments", appointments);
        model.addAttribute("providers", providerRepository.findOptions());
        model.addAttribute("services", serviceRepository.findAll());
        return "my-appointments";
    }

    @PostMapping("/my-appointments/{id}/cancel")
    public String cancelAppointment(
            @PathVariable long id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        try {
            appointmentService.cancel(id, user.id());
            redirectAttributes.addFlashAttribute("message", "Appointment cancelled.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/my-appointments";
    }

    @GetMapping("/advisor")
    public String advisorDashboard(HttpServletRequest request, Model model) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.ADVISOR);
        Provider provider = requireProvider(user.id());
        model.addAttribute("user", user);
        model.addAttribute("provider", provider);
        model.addAttribute("appointments", appointmentService.listForProvider(provider.id()));
        model.addAttribute("slots", slotService.listForProvider(provider.id()));
        model.addAttribute("services", serviceRepository.findAll());
        return "advisor-dashboard";
    }

    @PostMapping("/advisor/slots")
    public String addSlot(
            @RequestParam long serviceId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.ADVISOR);
        Provider provider = requireProvider(user.id());
        ZoneId zone = ZoneId.systemDefault();
        try {
            slotService.createSlot(
                    provider.id(),
                    serviceId,
                    startTime.atZone(zone).toOffsetDateTime(),
                    endTime.atZone(zone).toOffsetDateTime());
            redirectAttributes.addFlashAttribute("message", "Slot published.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/advisor";
    }

    @PostMapping("/advisor/slots/{id}/remove")
    public String removeSlot(
            @PathVariable long id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.ADVISOR);
        Provider provider = requireProvider(user.id());
        try {
            slotService.deleteUnbookedSlot(id, provider.id());
            redirectAttributes.addFlashAttribute("message", "Slot removed.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/advisor";
    }

    private Provider requireProvider(long userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", userId));
    }

    private String providerName(long providerId) {
        return providerRepository.findOptions().stream()
                .filter(p -> p.id() == providerId)
                .map(ProviderJdbcRepository.ProviderOption::fullName)
                .findFirst()
                .orElse("Provider #" + providerId);
    }

    private String serviceName(long serviceId) {
        return serviceRepository.findById(serviceId)
                .map(s -> s.name())
                .orElse("Service #" + serviceId);
    }

    private static String redirectForRole(SessionUser user) {
        if (user.hasRole(UserRole.ADVISOR)) {
            return "redirect:/advisor";
        }
        return "redirect:/slots";
    }
}
