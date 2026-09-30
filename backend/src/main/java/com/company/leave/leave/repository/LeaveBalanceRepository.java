package com.company.leave.leave.repository;

import com.company.leave.leave.domain.LeaveBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, int year);

    List<LeaveBalance> findByEmployeeIdOrderByYearDesc(Long employeeId);

    /** 해당 연도 직원 잔액(관리 전용 계정 제외). 대시보드·리포트·연차 촉진 집계용. */
    @Query("select b from LeaveBalance b where b.year = :year and b.employeeId not in "
            + "(select e.id from Employee e where e.systemAccount = true)")
    List<LeaveBalance> findByYearExcludingSystemAccounts(@Param("year") int year);
}
