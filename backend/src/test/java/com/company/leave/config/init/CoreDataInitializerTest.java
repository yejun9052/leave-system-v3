package com.company.leave.config.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.List;
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
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리 전용 계정(직원 아님) 생성. 아이디 admin, 기본 비밀번호 admin1234!.
 * 운영(app.admin.initial-password 미설정)은 첫 로그인 때 변경 강제, 로컬(지정)은 강제 없음.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("관리 전용 계정 초기 생성")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CoreDataInitializerTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MockEnvironment environment = new MockEnvironment();

    private CoreDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new CoreDataInitializer(employeeRepository, departmentRepository, passwordEncoder, environment);
        lenient().when(departmentRepository.findByParentIsNullOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(new Department("본사", null, 0)));
    }

    @Test
    void 운영_신규_설치는_아이디_admin_기본_비밀번호로_만들고_첫_로그인_때_변경을_강제한다() {
        when(employeeRepository.countAll()).thenReturn(0L);

        initializer.run(new DefaultApplicationArguments());

        Employee admin = 저장된_계정();
        assertThat(admin.getEmail()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("admin1234!", admin.getPasswordHash())).isTrue();
        assertThat(admin.isPasswordChangeRequired()).isTrue();
    }

    @Test
    void 관리_전용_계정은_직원이_아닌_시스템_계정이고_최고관리자_권한을_가진다() {
        when(employeeRepository.countAll()).thenReturn(0L);

        initializer.run(new DefaultApplicationArguments());

        Employee admin = 저장된_계정();
        assertThat(admin.isSystemAccount()).isTrue();
        assertThat(admin.getRoles()).containsExactly(Role.SYSTEM_ADMIN);
    }

    @Test
    void 로컬은_지정된_초기_비밀번호로_만들고_변경을_강제하지_않는다() {
        environment.setProperty("app.admin.initial-password", "local-pass!");
        when(employeeRepository.countAll()).thenReturn(0L);

        initializer.run(new DefaultApplicationArguments());

        Employee admin = 저장된_계정();
        assertThat(passwordEncoder.matches("local-pass!", admin.getPasswordHash())).isTrue();
        assertThat(admin.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void 이미_계정이_있으면_새로_만들지_않는다() {
        when(employeeRepository.countAll()).thenReturn(5L);

        initializer.run(new DefaultApplicationArguments());

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void 로컬에서는_기존_관리_계정의_비밀번호_변경_요구를_해제한다() {
        environment.setProperty("app.admin.initial-password", "admin1234!");
        Employee existing = Employee.builder().email("admin").passwordHash("h").name("시스템관리자")
                .roles(Set.of(Role.SYSTEM_ADMIN)).systemAccount(true).build();
        existing.requirePasswordChange();
        when(employeeRepository.countAll()).thenReturn(5L);
        when(employeeRepository.findByEmail("admin")).thenReturn(Optional.of(existing));

        initializer.run(new DefaultApplicationArguments());

        assertThat(existing.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void 운영에서는_기존_관리_계정의_비밀번호_변경_요구를_그대로_둔다() {
        when(employeeRepository.countAll()).thenReturn(5L);

        initializer.run(new DefaultApplicationArguments());

        verify(employeeRepository, never()).findByEmail(any());
    }

    private Employee 저장된_계정() {
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        return captor.getValue();
    }
}
