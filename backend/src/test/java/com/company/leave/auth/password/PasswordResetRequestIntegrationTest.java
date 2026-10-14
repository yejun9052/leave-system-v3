package com.company.leave.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.SessionTerminator;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 비밀번호 찾기 요청이 실제 트랜잭션 프록시·DB 에서 동작하는지. 모의 저장소 테스트로는 트랜잭션 없이
 * 삭제 쿼리(@Modifying)를 실행해 실패하는 문제를 잡지 못한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("비밀번호 찾기 요청(실제 PostgreSQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordResetRequestIntegrationTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:16-alpine");

    private static final String EMAIL = "user@company.com";
    private static final EmployeeRepository EMPLOYEES = mock(EmployeeRepository.class);

    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        DataSource ds = dataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        Long id = jdbc.queryForObject("INSERT INTO employees (email, password_hash, name, hire_date) "
                + "VALUES (?, 'x', '직원', DATE '2024-01-02') RETURNING id", Long.class, EMAIL);
        Employee employee = Employee.builder().email(EMAIL).passwordHash("x").name("직원")
                .hireDate(LocalDate.of(2024, 1, 2)).build();
        ReflectionTestUtils.setField(employee, "id", id);
        when(EMPLOYEES.findByEmail(any())).thenReturn(Optional.of(employee));
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void 재직_중인_계정이면_IP와_함께_요청해도_오류_없이_재설정_토큰이_저장된다() {
        context.getBean(PasswordResetService.class).requestReset(EMAIL, "10.0.0.1");
        context.getBean(PasswordResetService.class).requestReset(EMAIL, "10.0.0.1");

        // 다시 요청하면 이전 미사용 토큰은 지워지고 새 토큰 하나만 남는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isEqualTo(1);
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = PasswordResetTokenRepository.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return PasswordResetRequestIntegrationTest.dataSource();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.company.leave.auth.password.domain");
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            return emf;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        PasswordResetService passwordResetService(PasswordResetTokenRepository tokens,
                                                  PlatformTransactionManager transactionManager) {
            return new PasswordResetService(tokens, EMPLOYEES, mock(PasswordEncoder.class),
                    mock(ApplicationEventPublisher.class), mock(SessionTerminator.class), new ResetRequestLimiter(),
                    mock(AuditService.class), Clock.systemUTC(), new TransactionTemplate(transactionManager));
        }
    }
}
