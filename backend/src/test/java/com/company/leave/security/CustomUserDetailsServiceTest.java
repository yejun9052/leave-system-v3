package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인 사용자 조회: 이메일(관리 전용 계정은 아이디 admin)·id 로 찾아 principal 로 바꾸고, 없으면 UsernameNotFoundException.
 * 퇴사자는 찾되 principal 의 활성 여부가 꺼져 로그인할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 사용자 조회")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CustomUserDetailsServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void 이메일로_찾아_권한과_비밀번호_해시를_담는다() {
        Employee e = 직원(7L, "hong@company.com", EmployeeStatus.ACTIVE, Role.EMPLOYEE, Role.TEAM_LEAD);
        when(employeeRepository.findByEmail("hong@company.com")).thenReturn(Optional.of(e));

        UserDetails user = service.loadUserByUsername("hong@company.com");

        assertThat(user).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) user).getId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("hong@company.com");
        assertThat(user.getPassword()).isEqualTo("hash");
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(Role.EMPLOYEE.authority(), Role.TEAM_LEAD.authority());
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void 관리_전용_계정은_아이디로_찾는다() {
        Employee admin = 직원(1L, "admin", EmployeeStatus.ACTIVE, Role.SYSTEM_ADMIN);
        when(employeeRepository.findByEmail("admin")).thenReturn(Optional.of(admin));

        assertThat(service.loadUserByUsername("admin").getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(Role.SYSTEM_ADMIN.authority());
    }

    @Test
    void 없는_이메일이면_사용자를_찾을_수_없다는_예외를_던진다() {
        when(employeeRepository.findByEmail("none@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("none@company.com"))
                .isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("none@company.com");
    }

    @Test
    void 퇴사자는_찾지만_로그인할_수_없는_상태로_돌려준다() {
        Employee e = 직원(9L, "left@company.com", EmployeeStatus.RESIGNED, Role.EMPLOYEE);
        when(employeeRepository.findByEmail("left@company.com")).thenReturn(Optional.of(e));

        assertThat(service.loadUserByUsername("left@company.com").isEnabled()).isFalse();
    }

    @Test
    void id로_찾고_없으면_예외를_던진다() {
        when(employeeRepository.findById(7L))
                .thenReturn(Optional.of(직원(7L, "hong@company.com", EmployeeStatus.ACTIVE, Role.EMPLOYEE)));
        when(employeeRepository.findById(8L)).thenReturn(Optional.empty());

        assertThat(((UserPrincipal) service.loadById(7L)).getId()).isEqualTo(7L);
        assertThatThrownBy(() -> service.loadById(8L))
                .isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("id=8");
    }

    private static Employee 직원(Long id, String email, EmployeeStatus status, Role... roles) {
        Employee e = Employee.builder().email(email).passwordHash("hash").name("직원" + id)
                .status(status).roles(Set.of(roles)).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
