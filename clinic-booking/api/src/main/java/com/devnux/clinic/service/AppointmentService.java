package com.devnux.clinic.service;

import com.devnux.clinic.domain.AppUser;
import com.devnux.clinic.domain.Appointment;
import com.devnux.clinic.domain.AppointmentStatus;
import com.devnux.clinic.domain.Doctor;
import com.devnux.clinic.domain.Role;
import com.devnux.clinic.repo.AppUserRepository;
import com.devnux.clinic.repo.AppointmentRepository;
import com.devnux.clinic.repo.DoctorRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final AppUserRepository users;
    private final SlotService slots;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, DoctorRepository doctors,
                              AppUserRepository users, SlotService slots, Clock clock) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.users = users;
        this.slots = slots;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(Long patientId, Long doctorId, Instant startsAt, String reason) {
        AppUser patient = users.findById(patientId).orElseThrow(() -> ApiException.notFound("Patient"));
        if (patient.getRole() != Role.PATIENT) throw ApiException.forbidden("Only patients can book appointments");
        Doctor doctor = doctors.findWithHoursById(doctorId).orElseThrow(() -> ApiException.notFound("Doctor"));

        if (!startsAt.isAfter(clock.instant())) throw ApiException.badRequest("That time is in the past");
        if (!slots.isValidSlotStart(doctor, startsAt)) {
            throw ApiException.badRequest("That time is not one of the doctor's appointment slots");
        }

        Instant endsAt = startsAt.plusSeconds(doctor.getSlotMinutes() * 60L);
        try {
            return appointments.saveAndFlush(new Appointment(doctor, patient, startsAt, endsAt, reason));
        } catch (DataIntegrityViolationException e) {
            // The appointment_no_overlap exclusion constraint rejected it: someone else got the slot first.
            throw ApiException.conflict("Sorry, that slot was just booked by someone else");
        }
    }

    @Transactional(readOnly = true)
    public List<Appointment> forPatient(Long patientId) {
        return appointments.findByPatientIdOrderByStartsAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> forDoctor(Long doctorId, Instant from, Instant to) {
        return appointments.findByDoctorIdAndStartsAtBetweenOrderByStartsAt(doctorId, from, to);
    }

    @Transactional
    public Appointment cancel(Long userId, Long appointmentId) {
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("Appointment"));
        boolean isPatient = a.getPatient().getId().equals(userId);
        boolean isDoctor = a.getDoctor().getUser().getId().equals(userId);
        if (!isPatient && !isDoctor) throw ApiException.notFound("Appointment");
        if (a.getStatus() != AppointmentStatus.BOOKED) throw ApiException.conflict("This appointment is not active");
        if (!a.getStartsAt().isAfter(clock.instant())) throw ApiException.conflict("Past appointments cannot be cancelled");
        a.cancel();
        return a;
    }

    @Transactional
    public Appointment complete(Long doctorUserId, Long appointmentId) {
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("Appointment"));
        if (!a.getDoctor().getUser().getId().equals(doctorUserId)) throw ApiException.notFound("Appointment");
        if (a.getStatus() != AppointmentStatus.BOOKED) throw ApiException.conflict("This appointment is not active");
        if (a.getStartsAt().isAfter(clock.instant())) throw ApiException.conflict("This appointment hasn't started yet");
        a.complete();
        return a;
    }
}
