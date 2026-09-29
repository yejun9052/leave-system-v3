package com.company.leave.auth.dto;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import java.util.List;

public record MeResponse(
        Long id,
        String email,
        String name,
        String position,
        Long departmentId,
        String departmentName,
        List<String> roles) {

    public static MeResponse from(Employee e) {
        return new MeResponse(
                e.getId(),
                e.getEmail(),
                e.getName(),
                e.getPosition(),
                e.getDepartmentId(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getRoles().stream().map(Role::name).sorted().toList());
    }
}
