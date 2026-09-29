package com.company.leave.config.init;

import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 최초 실행 시 루트 부서와 시스템 관리자 계정을 생성한다. (이미 사용자가 있으면 skip)
 */
@Order(1)
@Component
public class CoreDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoreDataInitializer.class);

    private static final String ADMIN_EMAIL = "admin@company.com";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public CoreDataInitializer(EmployeeRepository employeeRepository,
                               DepartmentRepository departmentRepository,
                               PasswordEncoder passwordEncoder,
                               Environment environment) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employeeRepository.countAll() > 0) {
            return;
        }

        Department root = departmentRepository.findByParentIsNullOrderBySortOrderAscNameAsc()
                .stream().findFirst()
                .orElseGet(() -> departmentRepository.save(new Department("본사", null, 0)));

        // 설치마다 무작위 초기 비밀번호 생성(고정 기본값 미사용 → 배포본에 알려진 비번 없음).
        // 개발/테스트 편의를 위해 app.admin.initial-password 가 설정된 경우에만 그 값을 사용(운영은 미설정).
        String configured = environment.getProperty("app.admin.initial-password");
        boolean random = !StringUtils.hasText(configured);
        String initialPassword = random ? generateRandomPassword() : configured;

        Employee admin = Employee.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .name("시스템관리자")
                .employeeNo("ADMIN")
                .department(root)
                .position("관리자")
                .hireDate(LocalDate.now())
                .roles(EnumSet.of(Role.SUPER_ADMIN))
                .systemAccount(true)
                .build();
        employeeRepository.save(admin);

        // 무작위 생성 시에만, 최초 1회 로그로 안내(운영자가 확인 후 즉시 변경).
        if (random) {
            log.warn("================= 초기 관리자 계정 생성 =================");
            log.warn("  이메일       : {}", ADMIN_EMAIL);
            log.warn("  초기 비밀번호 : {}", initialPassword);
            log.warn("  ★ 이 비밀번호는 최초 1회만 로그에 표시됩니다.");
            log.warn("  ★ 로그인 후 [내 비밀번호 변경]으로 즉시 변경하세요.");
            log.warn("=======================================================");
        } else {
            log.info("=== 초기 관리자 계정 생성: {} (지정된 초기 비밀번호 사용 — 즉시 변경 권장) ===",
                    ADMIN_EMAIL);
        }
    }

    /** 안전한 무작위 초기 비밀번호(영숫자 16자). */
    private String generateRandomPassword() {
        byte[] buf = new byte[18];
        new SecureRandom().nextBytes(buf);
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
                .replaceAll("[^A-Za-z0-9]", "");
        return s.substring(0, Math.min(16, s.length()));
    }
}
