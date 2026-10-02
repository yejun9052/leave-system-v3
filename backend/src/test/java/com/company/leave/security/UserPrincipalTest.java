package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 세션(DB)에 직렬화되어 저장되는 인증 사용자 정보.
 */
@DisplayName("UserPrincipal")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserPrincipalTest {

    @Test
    void 역할은_ROLE_접두사를_붙인_권한으로_바뀐다() {
        UserPrincipal principal = UserPrincipal.from(직원(EmployeeStatus.ACTIVE, false));

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_HR_ADMIN");
        assertThat(principal.getUsername()).isEqualTo("user@company.com");
        assertThat(principal.getId()).isEqualTo(7L);
    }

    @Test
    void 재직_중이_아니면_비활성_사용자다() {
        assertThat(UserPrincipal.from(직원(EmployeeStatus.ACTIVE, false)).isEnabled()).isTrue();
        assertThat(UserPrincipal.from(직원(EmployeeStatus.RESIGNED, false)).isEnabled()).isFalse();
    }

    @Test
    void 비밀번호_변경_필요_여부를_직원_정보에서_가져온다() {
        assertThat(UserPrincipal.from(직원(EmployeeStatus.ACTIVE, true)).isPasswordChangeRequired()).isTrue();
        assertThat(UserPrincipal.from(직원(EmployeeStatus.ACTIVE, false)).isPasswordChangeRequired()).isFalse();
    }

    @Test
    void 인증_후_자격_증명을_지우면_비밀번호_해시가_남지_않는다() {
        UserPrincipal principal = UserPrincipal.from(직원(EmployeeStatus.ACTIVE, false));
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void 세션_저장을_위해_직렬화했다_복원해도_정보가_그대로다() throws Exception {
        UserPrincipal principal = UserPrincipal.from(직원(EmployeeStatus.ACTIVE, true));
        principal.eraseCredentials();

        UserPrincipal restored = 직렬화_후_복원(principal);

        assertThat(restored.getId()).isEqualTo(7L);
        assertThat(restored.getUsername()).isEqualTo("user@company.com");
        assertThat(restored.getName()).isEqualTo("테스트사원");
        assertThat(restored.getPassword()).isNull();
        assertThat(restored.isPasswordChangeRequired()).isTrue();
        assertThat(restored.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_HR_ADMIN");
    }

    @Test
    void 직렬화_버전_ID가_1로_고정돼_있다() {
        // 바뀌면 배포 후 기존 세션을 모두 읽지 못한다(SessionTolerantSecurityContextRepository 가 폐기 처리)
        assertThat(ReflectionTestUtils.getField(UserPrincipal.class, "serialVersionUID")).isEqualTo(1L);
    }

    // --- 테스트 데이터 ---

    private static Employee 직원(EmployeeStatus status, boolean passwordChangeRequired) {
        Employee employee = Employee.builder()
                .email("user@company.com")
                .passwordHash("$2a$10$hash")
                .name("테스트사원")
                .status(status)
                .roles(Set.of(Role.EMPLOYEE, Role.HR_ADMIN))
                .build();
        ReflectionTestUtils.setField(employee, "id", 7L);
        if (passwordChangeRequired) {
            employee.requirePasswordChange();
        }
        return employee;
    }

    private static UserPrincipal 직렬화_후_복원(UserPrincipal principal) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(principal);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (UserPrincipal) in.readObject();
        }
    }
}
