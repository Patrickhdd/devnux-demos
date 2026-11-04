package com.devnux.clinic.config;

import com.devnux.clinic.domain.AppUser;
import com.devnux.clinic.domain.Appointment;
import com.devnux.clinic.domain.Doctor;
import com.devnux.clinic.domain.Role;
import com.devnux.clinic.domain.WorkingHours;
import com.devnux.clinic.repo.AppUserRepository;
import com.devnux.clinic.repo.AppointmentRepository;
import com.devnux.clinic.repo.DoctorRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fills an empty database with demo accounts so the app can be tried straight away. */
@Component
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "demo1234";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ClinicProperties props;
    private final AppUserRepository users;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public DemoDataSeeder(ClinicProperties props, AppUserRepository users, DoctorRepository doctors,
                          AppointmentRepository appointments, PasswordEncoder passwords, Clock clock) {
        this.props = props;
        this.users = users;
        this.doctors = doctors;
        this.appointments = appointments;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seedDemoData() || users.count() > 0) return;
        String hash = passwords.encode(DEMO_PASSWORD);
        var weekdays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        Doctor amal = doctor(hash, "amal.haddad@clinic.test", "Dr. Amal Haddad", "General practice",
                "Family doctor for adults and children. Check-ups, vaccinations and everyday health concerns.", 30);
        weekdays.forEach(d -> amal.addWorkingHours(new WorkingHours(d, LocalTime.of(9, 0), LocalTime.of(13, 0))));
        weekdays.forEach(d -> amal.addWorkingHours(new WorkingHours(d, LocalTime.of(14, 0), LocalTime.of(17, 0))));
        doctors.save(amal);

        Doctor james = doctor(hash, "james.porter@clinic.test", "Dr. James Porter", "Dermatology",
                "Skin, hair and nail conditions, including mole checks and acne treatment.", 20);
        for (DayOfWeek d : EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            james.addWorkingHours(new WorkingHours(d, LocalTime.of(10, 0), LocalTime.of(16, 0)));
        }
        doctors.save(james);

        Doctor sara = doctor(hash, "sara.nassar@clinic.test", "Dr. Sara Nassar", "Cardiology",
                "Heart health, blood pressure and cholesterol. ECGs on site.", 45);
        for (DayOfWeek d : EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)) {
            sara.addWorkingHours(new WorkingHours(d, LocalTime.of(8, 30), LocalTime.of(15, 15)));
        }
        doctors.save(sara);

        AppUser patient = users.save(new AppUser("patient@clinic.test", hash, "Jordan Miller", Role.PATIENT));
        AppUser other = users.save(new AppUser("alex@clinic.test", hash, "Alex Kim", Role.PATIENT));

        // A few upcoming appointments so the doctor view isn't empty.
        LocalDate monday = LocalDate.now(clock.withZone(props.zone())).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        book(amal, patient, monday, LocalTime.of(9, 30), "Annual check-up");
        book(amal, other, monday, LocalTime.of(10, 0), "Flu symptoms");
        book(james, patient, monday.plusDays(2), LocalTime.of(11, 0), "Mole check");

        log.info("Demo data created. Sign in as patient@clinic.test or amal.haddad@clinic.test with password '{}'", DEMO_PASSWORD);
    }

    private Doctor doctor(String hash, String email, String name, String specialty, String bio, int slot) {
        AppUser user = users.save(new AppUser(email, hash, name, Role.DOCTOR));
        return new Doctor(user, specialty, bio, slot);
    }

    private void book(Doctor d, AppUser p, LocalDate date, LocalTime time, String reason) {
        var start = date.atTime(time).atZone(props.zone()).toInstant();
        appointments.save(new Appointment(d, p, start, start.plusSeconds(d.getSlotMinutes() * 60L), reason));
    }
}
