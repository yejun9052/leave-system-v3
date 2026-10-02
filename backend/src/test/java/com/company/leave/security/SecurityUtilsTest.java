package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 현재 로그인 사용자 조회: 로그인한 principal 의 id, 로그인하지 않았거나 익명이면 UNAUTHORIZED.
 */
@DisplayName("현재 로그인 사용자 조회")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그인한_사용자의_id를_돌려준다() {
        Employee e = Employee.builder().email("hong@company.com").passwordHash("h").name("홍길동")
                .roles(Set.of(Role.EMPLOYEE)).build();
        ReflectionTestUtils.setField(e, "id", 7L);
        UserPrincipal principal = UserPrincipal.from(e);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        assertThat(SecurityUtils.currentEmployeeId()).isEqualTo(7L);
        assertThat(SecurityUtils.currentPrincipal()).isSameAs(principal);
    }

    @Test
    void 로그인하지_않았으면_인증_필요_예외를_던진다() {
        assertThatThrownBy(SecurityUtils::currentEmployeeId)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void 익명_사용자는_로그인하지_않은_것으로_본다() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(SecurityUtils::currentEmployeeId)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
