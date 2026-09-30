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
import com.company.leave.mail.AccountMailEvents;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EmployeeService 의 비밀번호 관련 동작: 등록 시 임시 비밀번호, 관리자 재설정 메일, 본인 변경.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("직원 비밀번호 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmployeeServicePasswordTest {

    private static final String EMAIL = "new@company.com";

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LicenseService licenseService;
    @Mock
    private SessionTerminator sessionTerminator;
    @Mock
    private PasswordResetService passwordResetService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // 테스트 속도용 낮은 강도
    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employeeRepository, departmentRepository, passwordEncoder, eventPublisher,
                licenseService, sessionTerminator, generator, passwordResetService);
    }

    @Test
    void 등록하면_임시_비밀번호로_저장하고_변경을_요구하며_그_비밀번호를_메일로만_보낸다() {
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 10L);
            return e;
        });

        service.create(new EmployeeRequests.Create(EMAIL, "신입", null, null, null,
                LocalDate.of(2027, 1, 4), Set.of(Role.EMPLOYEE)));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        AccountMailEvents.AccountCreated mail = events.getAllValues().stream()
                .filter(AccountMailEvents.AccountCreated.class::isInstance)
                .map(AccountMailEvents.AccountCreated.class::cast)
                .findFirst().orElseThrow();

        assertThat(mail.email()).isEqualTo(EMAIL);
        assertThat(mail.temporaryPassword()).hasSize(12);
        assertThat(passwordEncoder.matches(mail.temporaryPassword(), saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().isPasswordChangeRequired()).isTrue();
    }

    @Test
    void 등록_응답에는_임시_비밀번호가_들어가지_않는다() {
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new EmployeeRequests.Create(EMAIL, "신입", null, null, null,
                LocalDate.of(2027, 1, 4), Set.of(Role.EMPLOYEE)));

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        String temporaryPassword = events.getAllValues().stream()
                .filter(AccountMailEvents.AccountCreated.class::isInstance)
                .map(e -> ((AccountMailEvents.AccountCreated) e).temporaryPassword())
                .findFirst().orElseThrow();
        assertThat(response.toString()).doesNotContain(temporaryPassword);
    }

    @Test
    void 관리자_초기화는_비밀번호를_바꾸지_않고_재설정_메일만_보낸다() {
        Employee employee = 직원("old-hash");
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        service.sendPasswordResetMail(5L);

        verify(passwordResetService).issue(employee);
        assertThat(employee.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void 본인_변경에_성공하면_새_비밀번호로_바뀌고_변경_요구가_풀린다() {
        Employee employee = 직원(passwordEncoder.encode("Temp#Pass123"));
        employee.requirePasswordChange();
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        service.changeMyPassword(5L, "Temp#Pass123", "myNewPassword!");

        assertThat(passwordEncoder.matches("myNewPassword!", employee.getPasswordHash())).isTrue();
        assertThat(employee.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void 현재_비밀번호가_틀리면_변경하지_않고_세션_만료가_아닌_400_입력_오류로_알린다() {
        Employee employee = 직원(passwordEncoder.encode("Temp#Pass123"));
        employee.requirePasswordChange();
        String before = employee.getPasswordHash();
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        // 401 이면 화면이 세션 만료로 보고 로그인 페이지로 보내 버린다
        assertThatThrownBy(() -> service.changeMyPassword(5L, "wrong", "myNewPassword!"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
                    assertThat(ex.getErrorCode().status().value()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("현재 비밀번호가 올바르지 않습니다.");
                });
        assertThat(employee.getPasswordHash()).isEqualTo(before);
        assertThat(employee.isPasswordChangeRequired()).isTrue();
        verify(passwordResetService, never()).issue(any());
    }

    private Employee 직원(String passwordHash) {
        Employee employee = Employee.builder()
                .email("user@company.com")
                .passwordHash(passwordHash)
                .name("홍길동")
                .build();
        ReflectionTestUtils.setField(employee, "id", 5L);
        return employee;
    }
}
