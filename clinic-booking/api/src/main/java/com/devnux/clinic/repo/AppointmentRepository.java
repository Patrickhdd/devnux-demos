package com.devnux.clinic.repo;

import com.devnux.clinic.domain.Appointment;
import com.devnux.clinic.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "patient"})
    List<Appointment> findByPatientIdOrderByStartsAtDesc(Long patientId);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "patient"})
    List<Appointment> findByDoctorIdAndStartsAtBetweenOrderByStartsAt(Long doctorId, Instant from, Instant to);

    List<Appointment> findByDoctorIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long doctorId, AppointmentStatus status, Instant to, Instant from);
}
