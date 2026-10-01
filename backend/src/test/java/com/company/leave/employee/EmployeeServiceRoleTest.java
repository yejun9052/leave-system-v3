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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayName("사용자 권한 제한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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
    void 다른_권한과_함께여도_시스템_관리자_권한으로는_직원을_만들_수_없다() {
        EmployeeRequests.Create req = new EmployeeRequests.Create("new@company.com", "신입", null, null, null,
                LocalDate.of(2024, 1, 1), Set.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN));
        expectError(() -> service.create(req), ErrorCode.SYSTEM_ADMIN_ROLE_RESTRICTED);
        verify(employees, never()).save(any());
        verify(employees, never()).lockForUserCreation();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void 다른_직원에게_시스템_관리자_권한을_줄_수_없고_정보도_바뀌지_않는다() {
        Employee target = employee(20L, false, Set.of(Role.EMPLOYEE));
        when(employees.findById(20L)).thenReturn(Optional.of(target));
        expectError(() -> service.update(20L, update(Set.of(Role.SYSTEM_ADMIN))),
                ErrorCode.SYSTEM_ADMIN_ROLE_RESTRICTED);
        assertThat(target.getEmail()).isEqualTo("user20@company.com");
        assertThat(target.getRoles()).containsExactly(Role.EMPLOYEE);
    }

    @Test
    void 인사관리자는_자신에게_시스템_관리자_권한을_줄_수_없다() {
        when(employees.findById(hr.getId())).thenReturn(Optional.of(hr));
        expectError(() -> service.update(hr.getId(), update(Set.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN))),
                ErrorCode.SYSTEM_ADMIN_ROLE_RESTRICTED);
        assertThat(hr.getRoles()).containsExactly(Role.HR_ADMIN);
    }

    @Test
    void 관리_전용_계정은_직원_수정으로_권한을_바꿀_수_없다() {
        Employee admin = employee(1L, true, Set.of(Role.SYSTEM_ADMIN));
        when(employees.findById(1L)).thenReturn(Optional.of(admin));
        for (Set<Role> roles : java.util.List.of(Set.of(Role.HR_ADMIN), Set.of(Role.SYSTEM_ADMIN),
                Set.of(Role.SYSTEM_ADMIN, Role.EMPLOYEE), Set.<Role>of())) {
            expectError(() -> service.update(1L, update(roles)), ErrorCode.SYSTEM_ACCOUNT_ROLE_IMMUTABLE);
        }
        assertThat(admin.getRoles()).containsExactly(Role.SYSTEM_ADMIN);
    }

    @Test
    void 관리_전용_계정도_자기_비밀번호는_바꿀_수_있다() {
        Employee admin = employee(1L, true, Set.of(Role.SYSTEM_ADMIN));
        admin.setTemporaryPassword(encoder.encode("old-password"));
        when(employees.findById(1L)).thenReturn(Optional.of(admin));
        service.changeMyPassword(1L, "old-password", "new-password");
        assertThat(encoder.matches("new-password", admin.getPasswordHash())).isTrue();
        assertThat(admin.isPasswordChangeRequired()).isFalse();
        assertThat(admin.getRoles()).containsExactly(Role.SYSTEM_ADMIN);
    }

    @Test
    void 일반_권한은_그대로_수정할_수_있다() {
        Employee target = employee(20L, false, Set.of(Role.EMPLOYEE));
        when(employees.findById(20L)).thenReturn(Optional.of(target));
        service.update(20L, update(Set.of(Role.HR_ADMIN, Role.TEAM_LEAD)));
        assertThat(target.getRoles()).containsExactlyInAnyOrder(Role.HR_ADMIN, Role.TEAM_LEAD);
    }

    @Test
    void 전사_결재자는_재직_중인_인사관리자와_시스템_관리자다() {
        when(employees.findIdsByAnyRoleAndStatus(Set.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN),
                com.company.leave.employee.domain.EmployeeStatus.ACTIVE)).thenReturn(java.util.List.of(1L, 11L));
        assertThat(service.activeAdminIds()).containsExactly(1L, 11L);
        verify(employees).findIdsByAnyRoleAndStatus(Set.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN),
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
