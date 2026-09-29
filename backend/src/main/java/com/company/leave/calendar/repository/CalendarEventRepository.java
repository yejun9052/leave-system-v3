package com.company.leave.calendar.repository;

import com.company.leave.calendar.domain.CalendarEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByLeaveRequestId(Long leaveRequestId);

    void deleteByLeaveRequestId(Long leaveRequestId);

    @Query("""
            select e from CalendarEvent e
            where e.startDate <= :end and e.endDate >= :start
            """)
    List<CalendarEvent> findBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
