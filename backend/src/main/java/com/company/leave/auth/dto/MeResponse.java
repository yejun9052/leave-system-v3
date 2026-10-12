package com.company.leave.auth.dto;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import java.time.LocalDate;

public record MeResponse(
        Long id,
        String email,
        String name,
        String position,
        String phone,
        Long departmentId,
        String departmentName,
        /** 입사일(내 정보 화면 표시용) */
        LocalDate hireDate,
        /** 권한(하나). 상위 권한은 하위 권한의 기능을 모두 쓴다 */
        Role role,
        boolean passwordChangeRequired,
        /** 관리 전용 계정(직원 아님): 휴가 신청·연차 부여 대상이 아니다. */
        boolean systemAccount) {

    public static MeResponse from(Employee e) {
        return new MeResponse(
                e.getId(),
                e.getEmail(),
                e.getName(),
                e.getPosition(),
                e.getPhone(),
                e.getDepartmentId(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getHireDate(),
                e.getRole(),
                e.isPasswordChangeRequired(),
                e.isSystemAccount());
    }
}
