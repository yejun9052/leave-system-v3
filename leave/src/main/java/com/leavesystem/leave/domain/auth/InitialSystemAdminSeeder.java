package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** 발급된 시스템 관리자 계정이 전혀 없을 때 환경변수로 한 계정을 발급한다. */
@Component
@RequiredArgsConstructor
public class InitialSystemAdminSeeder implements ApplicationRunner {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    @Value("${INITIAL_ADMIN_LOGIN_ID:}")
    private String initialLoginId;

    @Value("${INITIAL_ADMIN_OWNER:}")
    private String initialOwner;

    @Value("${INITIAL_ADMIN_PASSWORD:}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employees.existsByRoleAndPasswordHashIsNotNull(Role.SYS_ADMIN)) {
            return;
        }

        String loginId = initialLoginId.trim().toLowerCase(Locale.ROOT);
        String owner = initialOwner.trim();
        if (!loginId.matches("[a-z0-9._@-]{3,80}") || owner.isEmpty()
                || initialPassword.length() < 8) {
            throw new IllegalStateException("첫 시스템 관리자 발급에는 INITIAL_ADMIN_LOGIN_ID, "
                    + "INITIAL_ADMIN_OWNER, 8자 이상의 INITIAL_ADMIN_PASSWORD가 필요합니다.");
        }

        employees.save(Employee.systemAdmin(owner, loginId,
                passwordEncoder.encode(initialPassword)));
    }
}
