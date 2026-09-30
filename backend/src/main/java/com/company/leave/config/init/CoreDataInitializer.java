package com.company.leave.config.init;

import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.time.LocalDate;
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
 * 최초 실행 시 루트 부서와 관리 전용 계정(system_account, 직원 아님)을 생성한다. (이미 사용자가 있으면 skip)
 * 관리 전용 계정은 이메일 대신 아이디 {@value #ADMIN_LOGIN_ID} 로 로그인한다.
 */
@Order(1)
@Component
public class CoreDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoreDataInitializer.class);

    /** 관리 전용 계정 로그인 아이디(employees.email 컬럼에 저장). */
    static final String ADMIN_LOGIN_ID = "admin";
    /** 신규 설치 기본 비밀번호. 운영(app.admin.initial-password 미설정)은 첫 로그인 때 변경을 강제한다. */
    static final String DEFAULT_ADMIN_PASSWORD = "admin1234!";

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
            releaseLocalAdminPasswordChange();
            return;
        }

        Department root = departmentRepository.findByParentIsNullOrderBySortOrderAscNameAsc()
                .stream().findFirst()
                .orElseGet(() -> departmentRepository.save(new Department("본사", null, 0)));

        // 기본 비밀번호 admin1234!. 로컬/테스트는 app.admin.initial-password 로 지정(변경 강제 없음),
        // 운영(미설정)은 누구나 아는 기본값이므로 첫 로그인 때 변경을 강제한다.
        String configured = environment.getProperty("app.admin.initial-password");
        boolean localConfigured = StringUtils.hasText(configured);
        String initialPassword = localConfigured ? configured : DEFAULT_ADMIN_PASSWORD;

        Employee admin = Employee.builder()
                .email(ADMIN_LOGIN_ID)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .name("시스템관리자")
                .employeeNo("ADMIN")
                .department(root)
                .position("관리자")
                .hireDate(LocalDate.now())
                .roles(EnumSet.of(Role.SUPER_ADMIN))
                .systemAccount(true)
                .build();
        if (!localConfigured) {
            admin.requirePasswordChange();
        }
        employeeRepository.save(admin);

        if (localConfigured) {
            log.info("=== 관리 전용 계정 생성: 아이디 {} (지정된 초기 비밀번호) ===", ADMIN_LOGIN_ID);
        } else {
            log.warn("================= 관리 전용 계정 생성 =================");
            log.warn("  아이디   : {}", ADMIN_LOGIN_ID);
            log.warn("  비밀번호 : 기본 비밀번호(설치 안내 문서 참고)");
            log.warn("  ★ 첫 로그인 때 비밀번호를 반드시 변경해야 합니다.");
            log.warn("=====================================================");
        }
    }

    /**
     * 로컬/테스트(app.admin.initial-password 지정) 초기 관리자는 비밀번호 변경을 강제하지 않는다.
     * V12 가 기존 계정 전부를 변경 대상으로 표시하므로, 이미 있던 로컬 관리자는 여기서 해제한다.
     */
    private void releaseLocalAdminPasswordChange() {
        if (!StringUtils.hasText(environment.getProperty("app.admin.initial-password"))) {
            return;
        }
        employeeRepository.findByEmail(ADMIN_LOGIN_ID)
                .filter(Employee::isPasswordChangeRequired)
                .ifPresent(admin -> {
                    admin.setOwnPassword(admin.getPasswordHash());
                    log.info("로컬 관리 전용 계정({})의 비밀번호 변경 요구를 해제했습니다.", ADMIN_LOGIN_ID);
                });
    }
}
