package com.devnux.clinic.web;

import com.devnux.clinic.domain.AppUser;
import com.devnux.clinic.domain.Appointment;
import com.devnux.clinic.domain.Doctor;
import com.devnux.clinic.domain.WorkingHours;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Request and response bodies for the REST API. */
public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String fullName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserView(Long id, String email, String fullName, String role) {
        static UserView of(AppUser u) { return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name()); }
    }

    public record SessionView(String token, Instant expiresAt, UserView user) {}

    public record HoursView(DayOfWeek day, LocalTime start, LocalTime end) {
        static HoursView of(WorkingHours h) { return new HoursView(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()); }
    }

    public record DoctorView(Long id, String name, String specialty, String bio, int slotMinutes, List<HoursView> hours) {
        static DoctorView summary(Doctor d) {
            return new DoctorView(d.getId(), d.getUser().getFullName(), d.getSpecialty(), d.getBio(), d.getSlotMinutes(), null);
        }
        static DoctorView detail(Doctor d) {
            return new DoctorView(d.getId(), d.getUser().getFullName(), d.getSpecialty(), d.getBio(), d.getSlotMinutes(),
                    d.getWorkingHours().stream().map(HoursView::of).toList());
        }
    }

    public record BookRequest(@NotNull Long doctorId, @NotNull @Future Instant startsAt, @Size(max = 300) String reason) {}

    public record AppointmentView(Long id, Long doctorId, String doctorName, String specialty,
                                  Long patientId, String patientName, Instant startsAt, Instant endsAt,
                                  String status, String reason) {
        static AppointmentView of(Appointment a) {
            return new AppointmentView(a.getId(), a.getDoctor().getId(), a.getDoctor().getUser().getFullName(),
                    a.getDoctor().getSpecialty(), a.getPatient().getId(), a.getPatient().getFullName(),
                    a.getStartsAt(), a.getEndsAt(), a.getStatus().name(), a.getReason());
        }
    }
}
