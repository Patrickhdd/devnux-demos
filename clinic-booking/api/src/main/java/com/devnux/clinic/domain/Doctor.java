package com.devnux.clinic.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctor")
public class Doctor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(nullable = false)
    private String specialty;

    private String bio;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek, startTime")
    private List<WorkingHours> workingHours = new ArrayList<>();

    protected Doctor() {}

    public Doctor(AppUser user, String specialty, String bio, int slotMinutes) {
        this.user = user;
        this.specialty = specialty;
        this.bio = bio;
        this.slotMinutes = slotMinutes;
    }

    public void addWorkingHours(WorkingHours hours) {
        hours.setDoctor(this);
        workingHours.add(hours);
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getSpecialty() { return specialty; }
    public String getBio() { return bio; }
    public int getSlotMinutes() { return slotMinutes; }
    public List<WorkingHours> getWorkingHours() { return workingHours; }
}
