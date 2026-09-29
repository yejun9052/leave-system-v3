package com.company.leave.calendar.repository;

import com.company.leave.calendar.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);

    boolean existsByDate(LocalDate date);
}
