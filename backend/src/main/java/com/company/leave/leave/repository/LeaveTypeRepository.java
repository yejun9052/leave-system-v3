package com.company.leave.leave.repository;

import com.company.leave.leave.domain.LeaveType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findAllByOrderBySortOrderAscIdAsc();

    List<LeaveType> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);
}
