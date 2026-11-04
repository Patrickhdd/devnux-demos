package com.devnux.clinic.service;

import com.devnux.clinic.config.ClinicProperties;
import com.devnux.clinic.domain.Appointment;
import com.devnux.clinic.domain.AppointmentStatus;
import com.devnux.clinic.domain.Doctor;
import com.devnux.clinic.domain.WorkingHours;
import com.devnux.clinic.repo.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Works out which appointment slots a doctor has free on a given day. */
@Service
public class SlotService {

    public record Slot(Instant startsAt, Instant endsAt, boolean available) {}

    private final AppointmentRepository appointments;
    private final ClinicProperties props;
    private final Clock clock;

    public SlotService(AppointmentRepository appointments, ClinicProperties props, Clock clock) {
        this.appointments = appointments;
        this.props = props;
        this.clock = clock;
    }

    public List<Slot> slotsFor(Doctor doctor, LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        if (date.isBefore(today) || date.isAfter(today.plusDays(props.bookingWindowDays()))) {
            return slots;
        }

        Instant dayStart = date.atStartOfDay(props.zone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(props.zone()).toInstant();
        List<Appointment> booked = appointments.findByDoctorIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                doctor.getId(), AppointmentStatus.BOOKED, dayEnd, dayStart);
        Instant now = clock.instant();

        for (WorkingHours hours : doctor.getWorkingHours()) {
            if (hours.getDayOfWeek() != date.getDayOfWeek()) continue;
            LocalTime t = hours.getStartTime();
            while (!t.plusMinutes(doctor.getSlotMinutes()).isAfter(hours.getEndTime())) {
                ZonedDateTime start = date.atTime(t).atZone(props.zone());
                Instant s = start.toInstant();
                Instant e = start.plusMinutes(doctor.getSlotMinutes()).toInstant();
                boolean taken = booked.stream().anyMatch(a -> a.getStartsAt().isBefore(e) && a.getEndsAt().isAfter(s));
                slots.add(new Slot(s, e, !taken && s.isAfter(now)));
                LocalTime next = t.plusMinutes(doctor.getSlotMinutes());
                if (next.isBefore(t)) break; // wrapped past midnight
                t = next;
            }
        }
        slots.sort((a, b) -> a.startsAt().compareTo(b.startsAt()));
        return slots;
    }

    /** True if the given start time is exactly one of the doctor's slots on that day. */
    public boolean isValidSlotStart(Doctor doctor, Instant startsAt) {
        LocalDate date = startsAt.atZone(props.zone()).toLocalDate();
        return slotsFor(doctor, date).stream().anyMatch(s -> s.startsAt().equals(startsAt));
    }
}
