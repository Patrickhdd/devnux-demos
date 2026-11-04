package com.devnux.clinic.web;

import com.devnux.clinic.config.ClinicProperties;
import com.devnux.clinic.repo.DoctorRepository;
import com.devnux.clinic.service.ApiException;
import com.devnux.clinic.service.AppointmentService;
import com.devnux.clinic.web.Dtos.AppointmentView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Endpoints for signed-in doctors. Restricted to ROLE_DOCTOR in SecurityConfig. */
@RestController
@Transactional
@RequestMapping("/api/doctor")
public class DoctorScheduleController {
    private final DoctorRepository doctors;
    private final AppointmentService appointments;
    private final ClinicProperties props;

    public DoctorScheduleController(DoctorRepository doctors, AppointmentService appointments, ClinicProperties props) {
        this.doctors = doctors;
        this.appointments = appointments;
        this.props = props;
    }

    /** The doctor's appointments from {@code from} for {@code days} days, in the clinic's time zone. */
    @GetMapping("/schedule")
    public List<AppointmentView> schedule(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > 31) throw ApiException.badRequest("days must be between 1 and 31");
        var doctor = doctors.findByUserId(AppointmentController.userId(jwt))
                .orElseThrow(() -> ApiException.notFound("Doctor profile"));
        Instant start = from.atStartOfDay(props.zone()).toInstant();
        Instant end = from.plusDays(days).atStartOfDay(props.zone()).toInstant();
        return appointments.forDoctor(doctor.getId(), start, end).stream().map(AppointmentView::of).toList();
    }

    @PostMapping("/appointments/{id}/complete")
    public AppointmentView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return AppointmentView.of(appointments.complete(AppointmentController.userId(jwt), id));
    }
}
