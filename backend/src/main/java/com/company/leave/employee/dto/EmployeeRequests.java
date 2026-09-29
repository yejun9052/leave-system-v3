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
            @Size(max = 50) String employeeNo,
            Long departmentId,
            @Size(max = 50) String position,
            @Size(max = 30) String phone,
            @NotNull LocalDate hireDate,
            Set<Role> roles,
            @Size(min = 8, max = 72) String initialPassword) {
    }

    public record Update(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 50) String employeeNo,
            Long departmentId,
            @Size(max = 50) String position,
            @Size(max = 30) String phone,
            @NotNull LocalDate hireDate,
            Set<Role> roles) {
    }

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72) String newPassword) {
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
