package com.company.leave.backup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 백업 API 인가: 실행·목록은 인사관리자·시스템 관리자, 내려받기는 시스템 관리자만. 일반 직원·팀장은 모두 403.
 * DB는 기동하지 않는다.
 */
@DisplayName("백업 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private static final String NAME = "annual_leave_20261008_093000_manual.dump";
    private static final String DOWNLOAD = "/api/backups/" + NAME + "/download";

    @TempDir
    Path dir;

    private AnnotationConfigApplicationContext context;
    private BackupService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(BackupService.class);
        context.register(MethodSecurity.class);
        context.registerBean(BackupService.class, () -> service);
        context.registerBean(BackupController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(BackupController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원과_팀장은_백업을_보거나_실행하거나_내려받을_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(get("/api/backups")).andExpect(status().isForbidden());
            mvc.perform(post("/api/backups")).andExpect(status().isForbidden());
            mvc.perform(get(DOWNLOAD)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void 인사관리자는_백업을_보고_실행하지만_내려받을_수는_없다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(get("/api/backups")).andExpect(status().isOk());
        mvc.perform(post("/api/backups")).andExpect(status().isOk());
        mvc.perform(get(DOWNLOAD)).andExpect(status().isForbidden());
        verify(service).overview();
        verify(service).backup(Kind.MANUAL);
        verify(service, never()).resolve(anyString());
    }

    @Test
    void 시스템_관리자는_백업_파일을_내려받을_수_있다() throws Exception {
        Path file = Files.writeString(dir.resolve(NAME), "PGDMP");
        when(service.resolve(NAME)).thenReturn(file);
        authenticate(Set.of(Role.SYSTEM_ADMIN));

        mvc.perform(get(DOWNLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + NAME + "\""))
                .andExpect(content().bytes("PGDMP".getBytes()));
        mvc.perform(post("/api/backups")).andExpect(status().isOk());
        verify(service).backup(any());
    }

    private void authenticate(Set<Role> roles) {
        Employee employee = Employee.builder().email("user@company.com").name("사용자").roles(roles)
                .systemAccount(roles.contains(Role.SYSTEM_ADMIN)).build();
        ReflectionTestUtils.setField(employee, "id", 1L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
