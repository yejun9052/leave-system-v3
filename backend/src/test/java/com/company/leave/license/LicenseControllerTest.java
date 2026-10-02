package com.company.leave.license;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 라이선스 상태 API: 만료 임박 배너용이라 로그인한 누구나 본다. DB는 기동하지 않는다. */
@DisplayName("라이선스 상태 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LicenseControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private LicenseService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(LicenseService.class);
        context.register(MethodSecurity.class);
        context.registerBean(LicenseService.class, () -> service);
        context.registerBean(LicenseController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(LicenseController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원도_라이선스_만료일과_남은_일수를_본다() throws Exception {
        Employee employee = Employee.builder().email("user@company.com").name("사용자")
                .roles(Set.of(Role.EMPLOYEE)).build();
        ReflectionTestUtils.setField(employee, "id", 7L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(service.status()).thenReturn(new LicenseService.LicenseStatus(
                true, true, "OK", "주식회사 예시", LocalDate.of(2027, 1, 1), 91, 50));

        mvc.perform(get("/api/license"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value("2027-01-01"))
                .andExpect(jsonPath("$.data.daysLeft").value(91));
    }
}
