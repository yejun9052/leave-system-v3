package com.company.leave.employee.dto;

import com.company.leave.employee.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public final class EmployeeRequests {

    private EmployeeRequests() {
    }

    public record Create(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String name,
            Long departmentId,
            @Size(max = 50) String position,
            @Size(max = 30) String phone,
            @NotNull LocalDate hireDate,
            Set<Role> roles) {
    }

    /** email: 관리 전용 계정은 아이디(admin)가 바뀌지 않으므로 비워 보낸다. 그 외 직원은 필수. */
    public record Update(
            @Email String email,
            @NotBlank @Size(max = 100) String name,
            Long departmentId,
            @Size(max = 50) String position,
            @Size(max = 30) String phone,
            @NotNull LocalDate hireDate,
            Set<Role> roles) {
    }

    public record ChangeMyPassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record UpdateMyProfile(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 50) String position,
            @Size(max = 30) String phone) {
    }
}
