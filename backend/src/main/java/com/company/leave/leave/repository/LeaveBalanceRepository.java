package com.company.leave.leave.repository;

import com.company.leave.leave.domain.LeaveBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, int year);

    List<LeaveBalance> findByEmployeeIdOrderByYearDesc(Long employeeId);

    List<LeaveBalance> findByYear(int year);
}
