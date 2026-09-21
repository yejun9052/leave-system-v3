package com.leavesystem.leave.common.config;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** 서버 기동 시 이메일별로 누락된 기초 계정만 생성한다. */
@Component
@Profile("dev & !prod")
@RequiredArgsConstructor
public class InitialEmployeeSeeder implements ApplicationRunner {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate hireDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        createIfMissing("휴가관리자", "leaveAdmin@company.com", Role.HR_ADMIN, hireDate);
        createIfMissing("시스템관리자", "admin@company.com", Role.SYS_ADMIN, hireDate);
        createIfMissing("팀장", "manager@company.com", Role.LEADER, hireDate);
        createIfMissing("사원", "employee@company.com", Role.MEMBER, hireDate);
    }

    private void createIfMissing(String name, String email, Role role, LocalDate hireDate) {
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        employeeRepository.save(Employee.builder()
                .name(name)
                .email(email)
                .hireDate(hireDate)
                .role(role)
                .active(true)
                .build());
    }
}
