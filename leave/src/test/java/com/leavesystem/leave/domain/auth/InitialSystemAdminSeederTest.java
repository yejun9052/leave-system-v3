package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialSystemAdminSeederTest {

    @Mock
    EmployeeRepository employees;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    InitialSystemAdminSeeder seeder;

    @BeforeEach
    void 초기_발급값을_설정한다() {
        ReflectionTestUtils.setField(seeder, "initialLoginId", "TEST-ADMIN");
        ReflectionTestUtils.setField(seeder, "initialOwner", "담당자");
        ReflectionTestUtils.setField(seeder, "initialPassword", "test-password-123");
    }

    @Test
    void 관리자_계정이_없으면_하나를_발급한다() {
        when(passwordEncoder.encode("test-password-123")).thenReturn("encoded-password");

        seeder.run(null);

        var captor = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(captor.capture());
        Employee account = captor.getValue();
        assertThat(account.getRole()).isEqualTo(Role.SYS_ADMIN);
        assertThat(account.getLoginId()).isEqualTo("test-admin");
        assertThat(account.getName()).isEqualTo("담당자");
        assertThat(account.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(account.getEmail()).isNull();
        assertThat(account.getHireDate()).isNull();
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void 비활성화된_관리자_계정도_재발급하지_않는다() {
        when(employees.existsByRoleAndPasswordHashIsNotNull(Role.SYS_ADMIN)).thenReturn(true);

        seeder.run(null);

        verify(employees, never()).save(any());
    }

    @Test
    void 초기_비밀번호가_짧으면_계정을_발급하지_않는다() {
        ReflectionTestUtils.setField(seeder, "initialPassword", "short");

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8자 이상");
        verify(employees, never()).save(any());
    }
}
