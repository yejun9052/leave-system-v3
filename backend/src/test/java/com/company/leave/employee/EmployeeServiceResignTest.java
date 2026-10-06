package com.company.leave.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.auth.SessionTerminator;
import com.company.leave.auth.password.PasswordResetService;
import com.company.leave.auth.password.TemporaryPasswordGenerator;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeResponse;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.license.LicenseService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 퇴사 처리·복원과 본인 정보 수정. */
@ExtendWith(MockitoExtension.class)
@DisplayName("직원 퇴사·복원·내 정보 수정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmployeeServiceResignTest {

    @Mock private EmployeeRepository employees;
    @Mock private DepartmentRepository departments;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private LicenseService licenseService;
    @Mock private SessionTerminator sessionTerminator;
    @Mock private TemporaryPasswordGenerator passwordGenerator;
    @Mock private PasswordResetService passwordResetService;
    @Mock private DepartmentLeadSync leadSync;

    private EmployeeService service;
    private final Department 개발팀 = department(3L, "개발팀");

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employees, departments, passwordEncoder, eventPublisher, licenseService,
                sessionTerminator, passwordGenerator, passwordResetService, leadSync);
    }

    @Nested
    @DisplayName("퇴사")
    class 퇴사 {

        @Test
        void 퇴사_처리하면_상태와_퇴사일을_남기고_로그인_세션을_모두_끊는다() {
            Employee 직원 = employee(30L, Set.of(Role.EMPLOYEE), false);
            when(employees.findById(30L)).thenReturn(Optional.of(직원));

            service.resign(30L, LocalDate.of(2026, 10, 31));

            assertThat(직원.getStatus()).isEqualTo(EmployeeStatus.RESIGNED);
            assertThat(직원.getResignedDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            verify(sessionTerminator).terminateAll(30L);
            verify(leadSync).afterSave(직원, 3L, false);
        }

        @Test
        void 퇴사일을_주지_않으면_오늘로_처리한다() {
            Employee 직원 = employee(30L, Set.of(Role.EMPLOYEE), false);
            when(employees.findById(30L)).thenReturn(Optional.of(직원));

            service.resign(30L, null);

            assertThat(직원.getResignedDate()).isEqualTo(LocalDate.now());
        }

        @Test
        void 팀장이_퇴사하면_맡던_부서를_다른_팀장에게_넘기도록_팀장이었다고_알린다() {
            Employee 팀장 = employee(20L, Set.of(Role.EMPLOYEE, Role.TEAM_LEAD), false);
            when(employees.findById(20L)).thenReturn(Optional.of(팀장));

            service.resign(20L, LocalDate.of(2026, 10, 31));

            verify(leadSync).afterSave(팀장, 3L, true);
        }

        @Test
        void 관리_전용_계정은_없는_직원으로_보고_퇴사_처리하지_않는다() {
            Employee 관리계정 = employee(1L, Set.of(Role.SYSTEM_ADMIN), true);
            when(employees.findById(1L)).thenReturn(Optional.of(관리계정));

            assertThatThrownBy(() -> service.resign(1L, LocalDate.of(2026, 10, 31)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND));
            assertThat(관리계정.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
            verify(sessionTerminator, never()).terminateAll(anyLong());
        }

        @Test
        void 없는_직원은_퇴사_처리할_수_없다() {
            when(employees.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resign(99L, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND));
            verify(sessionTerminator, never()).terminateAll(anyLong());
        }
    }

    @Nested
    @DisplayName("복원")
    class 복원 {

        @Test
        void 복원하면_재직으로_돌리고_퇴사일을_지운다() {
            Employee 퇴사자 = employee(30L, Set.of(Role.EMPLOYEE, Role.TEAM_LEAD), false);
            퇴사자.resign(LocalDate.of(2026, 9, 30));
            when(employees.findById(30L)).thenReturn(Optional.of(퇴사자));

            service.reactivate(30L);

            assertThat(퇴사자.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
            assertThat(퇴사자.getResignedDate()).isNull();
            verify(leadSync).afterSave(퇴사자, 3L, false);
        }

        @Test
        void 관리_전용_계정은_복원_대상이_아니다() {
            Employee 관리계정 = employee(1L, Set.of(Role.SYSTEM_ADMIN), true);
            when(employees.findById(1L)).thenReturn(Optional.of(관리계정));

            assertThatThrownBy(() -> service.reactivate(1L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND));
            verify(leadSync, never()).afterSave(any(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("내 정보 수정")
    class 내_정보_수정 {

        @Test
        void 이름_직급_연락처만_바꾸고_부서와_권한은_그대로_둔다() {
            Employee 나 = employee(30L, Set.of(Role.EMPLOYEE, Role.TEAM_LEAD), false);
            when(employees.findById(30L)).thenReturn(Optional.of(나));

            EmployeeResponse updated = service.updateMyProfile(30L,
                    new EmployeeRequests.UpdateMyProfile("홍길순", "선임", "010-1234-5678"));

            assertThat(updated.name()).isEqualTo("홍길순");
            assertThat(updated.position()).isEqualTo("선임");
            assertThat(updated.phone()).isEqualTo("010-1234-5678");
            assertThat(updated.departmentId()).isEqualTo(3L);
            assertThat(updated.roles()).containsExactlyInAnyOrder("EMPLOYEE", "TEAM_LEAD");
        }

        @Test
        void 관리_전용_계정도_본인_정보는_고칠_수_있다() {
            Employee 관리계정 = employee(1L, Set.of(Role.SYSTEM_ADMIN), true);
            when(employees.findById(1L)).thenReturn(Optional.of(관리계정));

            EmployeeResponse updated = service.updateMyProfile(1L,
                    new EmployeeRequests.UpdateMyProfile("관리자", null, null));

            assertThat(updated.name()).isEqualTo("관리자");
        }
    }

    private Employee employee(Long id, Set<Role> roles, boolean systemAccount) {
        Employee e = Employee.builder().email("e" + id + "@company.com").name("홍길동").position("사원")
                .phone("010-0000-0000").passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1))
                .department(개발팀).roles(roles).systemAccount(systemAccount).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static Department department(Long id, String name) {
        Department d = new Department(name, null, 0);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }
}
