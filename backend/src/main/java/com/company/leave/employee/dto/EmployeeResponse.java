package com.company.leave.employee.dto;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import java.time.LocalDate;
import java.util.List;

public record EmployeeResponse(
        Long id,
        String email,
        String name,
        String employeeNo,
        Long departmentId,
        String departmentName,
        String position,
        String phone,
        LocalDate hireDate,
        EmployeeStatus status,
        List<String> roles) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getEmail(),
                e.getName(),
                e.getEmployeeNo(),
                e.getDepartmentId(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getPosition(),
                e.getPhone(),
                e.getHireDate(),
                e.getStatus(),
                e.getRoles().stream().map(Role::name).sorted().toList());
    }
}
