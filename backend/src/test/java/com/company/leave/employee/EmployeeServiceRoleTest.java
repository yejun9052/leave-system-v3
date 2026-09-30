package com.company.leave.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.auth.SessionTerminator;
import com.company.leave.auth.password.PasswordResetService;
import com.company.leave.auth.password.TemporaryPasswordGenerator;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.license.LicenseService;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceRoleTest {
    @Mock private EmployeeRepository employees;
    @Mock private DepartmentRepository departments;
    @Mock private ApplicationEventPublisher events;
    @Mock private LicenseService license;
    @Mock private SessionTerminator sessions;
    @Mock private PasswordResetService resets;

    private EmployeeService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private Employee hr;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employees, departments, encoder, events, license, sessions,
                new TemporaryPasswordGenerator(), resets);
        hr = employee(11L, false, Set.of(Role.HR_ADMIN));
        UserPrincipal principal = UserPrincipal.from(hr);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hrCannotCreateSuperAdminEvenWithOtherRoles() {
        EmployeeRequests.Create req = new EmployeeRequests.Create("new@company.com", "신입", null, null, null,
                LocalDate.of(2024, 1, 1), Set.of(Role.HR_ADMIN, Role.SUPER_ADMIN));
        expectError(() -> service.create(req), ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
        verify(employees, never()).save(any());
        verify(employees, never()).lockForUserCreation();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void hrCannotGrantSuperAdminToAnotherEmployeeAndProfileIsNotChanged() {
        Employee target = employee(20L, false, Set.of(Role.EMPLOYEE));
        when(employees.findById(20L)).thenReturn(Optional.of(target));
        expectError(() -> service.update(20L, update(Set.of(Role.SUPER_ADMIN))),
                ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
        assertThat(target.getEmail()).isEqualTo("user20@company.com");
        assertThat(target.getRoles()).containsExactly(Role.EMPLOYEE);
    }

    @Test
    void hrCannotGrantSuperAdminToSelf() {
        when(employees.findById(hr.getId())).thenReturn(Optional.of(hr));
        expectError(() -> service.update(hr.getId(), update(Set.of(Role.HR_ADMIN, Role.SUPER_ADMIN))),
                ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
        assertThat(hr.getRoles()).containsExactly(Role.HR_ADMIN);
    }

    @Test
    void systemAccountCannotBeChangedThroughEmployeeUpdate() {
        Employee admin = employee(1L, true, Set.of(Role.SUPER_ADMIN));
        when(employees.findById(1L)).thenReturn(Optional.of(admin));
        for (Set<Role> roles : java.util.List.of(Set.of(Role.HR_ADMIN), Set.of(Role.SUPER_ADMIN),
                Set.of(Role.SUPER_ADMIN, Role.EMPLOYEE), Set.<Role>of())) {
            expectError(() -> service.update(1L, update(roles)), ErrorCode.SYSTEM_ACCOUNT_ROLE_IMMUTABLE);
        }
        assertThat(admin.getRoles()).containsExactly(Role.SUPER_ADMIN);
    }

    @Test
    void systemAccountCanStillChangeOwnPassword() {
        Employee admin = employee(1L, true, Set.of(Role.SUPER_ADMIN));
        admin.setTemporaryPassword(encoder.encode("old-password"));
        when(employees.findById(1L)).thenReturn(Optional.of(admin));
        service.changeMyPassword(1L, "old-password", "new-password");
        assertThat(encoder.matches("new-password", admin.getPasswordHash())).isTrue();
        assertThat(admin.isPasswordChangeRequired()).isFalse();
        assertThat(admin.getRoles()).containsExactly(Role.SUPER_ADMIN);
    }

    @Test
    void ordinaryRolesRemainEditable() {
        Employee target = employee(20L, false, Set.of(Role.EMPLOYEE));
        when(employees.findById(20L)).thenReturn(Optional.of(target));
        service.update(20L, update(Set.of(Role.HR_ADMIN, Role.TEAM_LEAD)));
        assertThat(target.getRoles()).containsExactlyInAnyOrder(Role.HR_ADMIN, Role.TEAM_LEAD);
    }

    @Test
    void approvalRecipientsAreOnlyActiveHrAdmins() {
        when(employees.findIdsByAnyRoleAndStatus(Set.of(Role.HR_ADMIN),
                com.company.leave.employee.domain.EmployeeStatus.ACTIVE)).thenReturn(java.util.List.of(11L));
        assertThat(service.activeAdminIds()).containsExactly(11L);
        verify(employees).findIdsByAnyRoleAndStatus(Set.of(Role.HR_ADMIN),
                com.company.leave.employee.domain.EmployeeStatus.ACTIVE);
    }

    private EmployeeRequests.Update update(Set<Role> roles) {
        return new EmployeeRequests.Update("changed@company.com", "수정", null, null, null,
                LocalDate.of(2024, 1, 1), roles);
    }

    private Employee employee(Long id, boolean system, Set<Role> roles) {
        Employee e = Employee.builder().email("user" + id + "@company.com").name("직원")
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1)).systemAccount(system).roles(roles).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private void expectError(Runnable operation, ErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(code);
            assertThat(ex.getErrorCode().status().value()).isEqualTo(400);
        });
    }
}
