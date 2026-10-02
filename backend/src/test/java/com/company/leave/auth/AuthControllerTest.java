package com.company.leave.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.auth.password.PasswordResetService;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 인증 API 의 입력 검사와 서비스 위임. 세션·CSRF 쿠키 동작은 보안 필터가 필요해 여기서 다루지 않는다
 * (로그인·로그아웃 자체는 AuthServiceLoginTest·AuthServiceLogoutTest). DB는 기동하지 않는다.
 */
@DisplayName("인증 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private AuthService authService;
    private PasswordResetService passwordResetService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        authService = mock(AuthService.class);
        passwordResetService = mock(PasswordResetService.class);
        context.register(MethodSecurity.class);
        context.registerBean(AuthService.class, () -> authService);
        context.registerBean(PasswordResetService.class, () -> passwordResetService);
        context.registerBean(AuthController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(AuthController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 로그인은_아이디와_비밀번호를_서비스에_넘긴다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@company.com\",\"password\":\"secret123!\"}")).andExpect(status().isOk());
        verify(authService).login(argThat(r -> r.email().equals("user@company.com")
                && r.password().equals("secret123!")), any(), any());
    }

    @Test
    void 아이디나_비밀번호가_비면_400이고_로그인을_시도하지_않는다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"secret123!\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@company.com\"}")).andExpect(status().isBadRequest());
        verify(authService, never()).login(any(), any(), any());
    }

    @Test
    void 로그아웃은_서비스에_맡긴다() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
        verify(authService).logout(any(), any());
    }

    @Test
    void 비밀번호_재설정_요청은_이메일_형식이_맞을_때만_서비스에_넘긴다() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}")).andExpect(status().isBadRequest());
        verify(passwordResetService, never()).requestReset(anyString());

        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@company.com\"}")).andExpect(status().isOk());
        verify(passwordResetService).requestReset("user@company.com");
    }

    @Test
    void 새_비밀번호가_8자_미만이거나_토큰이_없으면_400이다() throws Exception {
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\",\"newPassword\":\"short1!\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"longenough1!\"}")).andExpect(status().isBadRequest());
        verify(passwordResetService, never()).confirm(anyString(), anyString());

        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\",\"newPassword\":\"longenough1!\"}")).andExpect(status().isOk());
        verify(passwordResetService).confirm("abc", "longenough1!");
    }

    @Test
    void 내_정보는_로그인한_본인_id로_조회한다() throws Exception {
        Employee employee = Employee.builder().email("user@company.com").name("사용자")
                .roles(Set.of(Role.EMPLOYEE)).build();
        ReflectionTestUtils.setField(employee, "id", 7L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        mvc.perform(get("/api/auth/me")).andExpect(status().isOk());
        verify(authService).me(7L);
    }
}
