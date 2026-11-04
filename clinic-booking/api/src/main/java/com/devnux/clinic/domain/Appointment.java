package com.devnux.clinic.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private AppUser patient;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Appointment() {}

    public Appointment(Doctor doctor, AppUser patient, Instant startsAt, Instant endsAt, String reason) {
        this.doctor = doctor;
        this.patient = patient;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public void cancel() { this.status = AppointmentStatus.CANCELLED; }
    public void complete() { this.status = AppointmentStatus.COMPLETED; }

    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public AppUser getPatient() { return patient; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public AppointmentStatus getStatus() { return status; }
    public String getReason() { return reason; }
}
