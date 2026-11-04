package com.devnux.clinic.repo;

import com.devnux.clinic.domain.Doctor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    @EntityGraph(attributePaths = {"user"})
    @Query("select d from Doctor d order by d.user.fullName")
    List<Doctor> findAllWithUser();

    @EntityGraph(attributePaths = {"user", "workingHours"})
    Optional<Doctor> findWithHoursById(Long id);

    @EntityGraph(attributePaths = {"user"})
    Optional<Doctor> findByUserId(Long userId);
}
