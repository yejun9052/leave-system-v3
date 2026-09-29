package com.company.leave.policy.repository;

import com.company.leave.policy.domain.BlackoutPeriod;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlackoutPeriodRepository extends JpaRepository<BlackoutPeriod, Long> {

    List<BlackoutPeriod> findAllByOrderByStartDateAsc();

    @Query("""
            select (count(b) > 0) from BlackoutPeriod b
            where b.startDate <= :end and b.endDate >= :start
            """)
    boolean existsOverlap(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
