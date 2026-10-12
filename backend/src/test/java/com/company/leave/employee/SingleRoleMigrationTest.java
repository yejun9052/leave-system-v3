package com.company.leave.employee;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V11: 여러 권한(employee_roles) → 권한 하나(employees.role). 가진 권한 중 가장 높은 것 하나만 남는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("권한 하나로 옮기기(V11)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SingleRoleMigrationTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void 기존_데이터는_가장_높은_권한_하나만_남고_예전_표는_없어진다() {
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("10").load().migrate();

        사람(jdbc, 1, "admin", "SYSTEM_ADMIN");
        사람(jdbc, 2, "hr@company.com", "EMPLOYEE", "TEAM_LEAD", "HR_ADMIN");
        사람(jdbc, 3, "lead@company.com", "EMPLOYEE", "TEAM_LEAD");
        사람(jdbc, 4, "emp@company.com", "EMPLOYEE");
        사람(jdbc, 5, "none@company.com");

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForList("SELECT email || '=' || role FROM employees ORDER BY id", String.class))
                .containsExactly("admin=SYSTEM_ADMIN", "hr@company.com=HR_ADMIN", "lead@company.com=TEAM_LEAD",
                        "emp@company.com=EMPLOYEE", "none@company.com=EMPLOYEE");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.employee_roles') IS NULL", Boolean.class)).isTrue();
    }

    private static void 사람(JdbcTemplate jdbc, long id, String email, String... roles) {
        jdbc.update("INSERT INTO employees (id, email, password_hash, name, hire_date) VALUES (?, ?, 'x', '직원', DATE '2024-01-02')",
                id, email);
        for (String role : roles) {
            jdbc.update("INSERT INTO employee_roles (employee_id, role) VALUES (?, ?)", id, role);
        }
    }
}
