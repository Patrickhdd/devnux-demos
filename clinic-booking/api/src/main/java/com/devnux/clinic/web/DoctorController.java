package com.devnux.clinic.web;

import com.devnux.clinic.repo.DoctorRepository;
import com.devnux.clinic.service.ApiException;
import com.devnux.clinic.service.SlotService;
import com.devnux.clinic.web.Dtos.DoctorView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorRepository doctors;
    private final SlotService slots;

    public DoctorController(DoctorRepository doctors, SlotService slots) {
        this.doctors = doctors;
        this.slots = slots;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DoctorView> list() {
        return doctors.findAllWithUser().stream().map(DoctorView::summary).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DoctorView get(@PathVariable Long id) {
        return DoctorView.detail(doctors.findWithHoursById(id).orElseThrow(() -> ApiException.notFound("Doctor")));
    }

    /** Slots for one day, in the clinic's time zone. */
    @GetMapping("/{id}/slots")
    @Transactional(readOnly = true)
    public List<SlotService.Slot> slots(@PathVariable Long id,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var doctor = doctors.findWithHoursById(id).orElseThrow(() -> ApiException.notFound("Doctor"));
        return slots.slotsFor(doctor, date);
    }
}
