package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * AccountStateFilter 단위 테스트: 세션 사용자의 최신 상태(DB)로 요청을 통과/차단하는지 확인.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("계정 상태 필터")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccountStateFilterTest {

    private static final long EMPLOYEE_ID = 3L;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private final RestAuthEntryPoints entryPoints = new RestAuthEntryPoints(JsonMapper.builder().build());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 비밀번호_변경이_필요하면_허용_목록_외_요청은_403_PASSWORD_CHANGE_REQUIRED() throws Exception {
        세션_로그인(직원(EmployeeStatus.ACTIVE, true));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = 실행("GET", "/api/leave-requests/me", chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(chain.getRequest()).as("다음 필터로 넘어가지 않음").isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "POST, /api/auth/login",
        "GET, /api/auth/csrf",
        "GET, /api/auth/me",
        "POST, /api/auth/logout",
        "PATCH, /api/employees/me/password",
        "GET, /api/license",
    })
    void 비밀번호_변경이_필요해도_허용_목록은_통과한다(String method, String uri) throws Exception {
        세션_로그인(직원(EmployeeStatus.ACTIVE, true));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = 실행(method, uri, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 허용_목록과_경로가_같아도_메서드가_다르면_차단한다() throws Exception {
        세션_로그인(직원(EmployeeStatus.ACTIVE, true));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = 실행("PUT", "/api/employees/me/profile", chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 비밀번호_변경이_필요_없으면_그대로_통과한다() throws Exception {
        세션_로그인(직원(EmployeeStatus.ACTIVE, false));

        MockFilterChain chain = new MockFilterChain();
        실행("GET", "/api/leave-requests/me", chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 퇴사자는_세션을_지우고_401() throws Exception {
        세션_로그인(직원(EmployeeStatus.RESIGNED, false));
        MockHttpSession session = new MockHttpSession();

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AccountStateFilter(userDetailsService, entryPoints).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        assertThat(chain.getRequest()).isNull();
    }

    // --- 테스트 도구 ---

    private MockHttpServletResponse 실행(String method, String uri, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AccountStateFilter(userDetailsService, entryPoints)
                .doFilter(new MockHttpServletRequest(method, uri), response, chain);
        return response;
    }

    /** 세션에서 복원된 인증(로그인 시점 principal) + DB 에서 새로 읽힐 최신 상태. */
    private void 세션_로그인(Employee latest) {
        UserPrincipal atLogin = UserPrincipal.from(직원(EmployeeStatus.ACTIVE, false));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(atLogin, null, atLogin.getAuthorities()));
        when(userDetailsService.loadById(EMPLOYEE_ID)).thenReturn(UserPrincipal.from(latest));
    }

    private Employee 직원(EmployeeStatus status, boolean passwordChangeRequired) {
        Employee employee = Employee.builder()
                .email("user@company.com")
                .passwordHash("hash")
                .name("홍길동")
                .status(status)
                .build();
        if (passwordChangeRequired) {
            employee.requirePasswordChange();
        }
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        return employee;
    }
}
