package com.company.leave.leave.repository;

import com.company.leave.leave.domain.LeaveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findAllByOrderBySortOrderAscIdAsc();

    List<LeaveType> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<LeaveType> findByCode(String code);

    boolean existsByCode(String code);
}
