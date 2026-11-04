package com.devnux.clinic.web;

import com.devnux.clinic.service.AppointmentService;
import com.devnux.clinic.web.Dtos.AppointmentView;
import com.devnux.clinic.web.Dtos.BookRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Transactional
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) { this.appointments = appointments; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentView book(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookRequest req) {
        return AppointmentView.of(appointments.book(userId(jwt), req.doctorId(), req.startsAt(), req.reason()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentView> mine(@AuthenticationPrincipal Jwt jwt) {
        return appointments.forPatient(userId(jwt)).stream().map(AppointmentView::of).toList();
    }

    @PostMapping("/{id}/cancel")
    public AppointmentView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return AppointmentView.of(appointments.cancel(userId(jwt), id));
    }

    static Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
