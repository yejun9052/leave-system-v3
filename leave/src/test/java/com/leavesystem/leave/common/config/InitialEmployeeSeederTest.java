package com.leavesystem.leave.common.config;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:initial-employees;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InitialEmployeeSeeder.class, JpaAuditingConfig.class})
@ActiveProfiles("dev")
class InitialEmployeeSeederTest {

    @Autowired
    private InitialEmployeeSeeder seeder;

    @Autowired
    private EmployeeRepository employees;

    @BeforeEach
    void clearStartupAccounts() {
        employees.deleteAllInBatch();
    }

    @Test
    void createsFourAccountsAndCanRunAgainWithoutDuplicates() {
        seeder.run(null);
        employees.flush();
        var originalIds = employees.findAll().stream().map(Employee::getId).toList();

        seeder.run(null);
        employees.flush();

        assertThat(employees.findAll()).hasSize(4)
                .allSatisfy(employee -> {
                    assertThat(employee.isActive()).isTrue();
                    assertThat(employee.getDepartment()).isNull();
                    assertThat(employee.getHireDate()).isNotNull();
                    assertThat(employee.getCreatedAt()).isNotNull();
                });
        assertThat(employees.findAll().stream().map(Employee::getId).toList())
                .containsExactlyInAnyOrderElementsOf(originalIds);
        assertThat(employees.findAll().stream().collect(Collectors.toMap(Employee::getEmail, Employee::getRole)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "leaveAdmin@company.com", Role.HR_ADMIN,
                        "admin@company.com", Role.SYS_ADMIN,
                        "manager@company.com", Role.LEADER,
                        "employee@company.com", Role.MEMBER));
    }

    @Test
    void preservesExistingAccountEvenWithDifferentEmailCaseAndFillsMissingAccounts() {
        Employee existing = employees.saveAndFlush(Employee.builder()
                .name("기존 관리자")
                .email("ADMIN@company.com")
                .hireDate(LocalDate.of(2020, 1, 2))
                .role(Role.MEMBER)
                .active(false)
                .build());
        var originalUpdatedAt = existing.getUpdatedAt();

        seeder.run(null);
        employees.flush();

        assertThat(employees.count()).isEqualTo(4);
        Employee preserved = employees.findById(existing.getId()).orElseThrow();
        assertThat(preserved.getName()).isEqualTo("기존 관리자");
        assertThat(preserved.getEmail()).isEqualTo("ADMIN@company.com");
        assertThat(preserved.getHireDate()).isEqualTo(LocalDate.of(2020, 1, 2));
        assertThat(preserved.getRole()).isEqualTo(Role.MEMBER);
        assertThat(preserved.isActive()).isFalse();
        assertThat(preserved.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }
}
