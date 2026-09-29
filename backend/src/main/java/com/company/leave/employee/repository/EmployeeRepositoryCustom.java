package com.company.leave.employee.repository;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmployeeRepositoryCustom {

    Page<Employee> search(EmployeeSearchCondition condition, Pageable pageable);
}
