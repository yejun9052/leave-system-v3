package com.company.leave.calendar.repository;

import com.company.leave.calendar.domain.CalendarEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByLeaveRequestId(Long leaveRequestId);

    void deleteByLeaveRequestId(Long leaveRequestId);

    /**
     * 관리자·팀장이 등록한 그 부서 전용 일정(부서 범위)을 지운다. 부서 삭제 때 사용.
     * 휴가 일정은 지우지 않는다(부서가 지워지면 department_id 만 비워진다).
     */
    @Modifying
    @Query("""
            delete from CalendarEvent e
            where e.departmentId = :departmentId
              and e.source = com.company.leave.calendar.domain.CalendarEventSource.ADMIN_EVENT
              and e.scope = com.company.leave.calendar.domain.CalendarEventScope.DEPARTMENT
            """)
    int deleteDepartmentEvents(@Param("departmentId") Long departmentId);

    @Query("""
            select e from CalendarEvent e
            where e.startDate <= :end and e.endDate >= :start
            """)
    List<CalendarEvent> findBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
