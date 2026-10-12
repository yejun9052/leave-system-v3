package com.company.leave.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * 권한 상속: 한 사람은 권한을 하나만 갖고, 상위 권한은 하위 권한의 기능을 모두 쓴다.
 * 시스템 관리자 > 인사관리자 > 팀장 > 사원.
 */
@DisplayName("권한 상속")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RoleTest {

    @Test
    void 상위_권한은_자신과_하위_권한을_포함하고_상위는_포함하지_않는다() {
        assertThat(Role.HR_ADMIN.includes(Role.HR_ADMIN)).isTrue();
        assertThat(Role.HR_ADMIN.includes(Role.TEAM_LEAD)).isTrue();
        assertThat(Role.HR_ADMIN.includes(Role.EMPLOYEE)).isTrue();
        assertThat(Role.HR_ADMIN.includes(Role.SYSTEM_ADMIN)).isFalse();
        assertThat(Role.TEAM_LEAD.includes(Role.HR_ADMIN)).isFalse();
        assertThat(Role.EMPLOYEE.includes(Role.TEAM_LEAD)).isFalse();
        assertThat(Role.SYSTEM_ADMIN.includes(Role.EMPLOYEE)).isTrue();
        assertThat(Role.EMPLOYEE.includes(null)).isFalse();
    }

    @Test
    void 직원의_권한_확인도_상속을_따른다() {
        Employee hr = Employee.builder().email("hr").name("인사").role(Role.HR_ADMIN).build();
        Employee lead = Employee.builder().email("lead").name("팀장").role(Role.TEAM_LEAD).build();

        assertThat(hr.hasRole(Role.TEAM_LEAD)).isTrue();
        assertThat(lead.hasRole(Role.EMPLOYEE)).isTrue();
        assertThat(lead.hasRole(Role.HR_ADMIN)).isFalse();
    }

    @Test
    void 권한을_정하지_않으면_사원이다() {
        assertThat(Employee.builder().email("e").name("신입").build().getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void 여러_권한_중_가장_높은_하나를_고른다() {
        assertThat(Role.highest(Role.EMPLOYEE, Role.TEAM_LEAD, Role.HR_ADMIN)).isEqualTo(Role.HR_ADMIN);
        assertThat(Role.highest(List.of(Role.TEAM_LEAD, Role.EMPLOYEE))).isEqualTo(Role.TEAM_LEAD);
        assertThat(Role.highest(List.of())).isEqualTo(Role.EMPLOYEE);
        assertThat(Role.highest((List<Role>) null)).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void 보안_설정의_권한_계층은_인사관리자에게_팀장과_사원_권한을_준다() {
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy(Role.hierarchy());

        assertThat(hierarchy.getReachableGrantedAuthorities(AuthorityUtils.createAuthorityList("ROLE_HR_ADMIN")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HR_ADMIN", "ROLE_TEAM_LEAD", "ROLE_EMPLOYEE");
        assertThat(hierarchy.getReachableGrantedAuthorities(AuthorityUtils.createAuthorityList("ROLE_TEAM_LEAD")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_TEAM_LEAD", "ROLE_EMPLOYEE");
    }
}
